def projectName="" // BPM, HIP or E-SALES
def buildType = "" // MavenDeploy
def deploymentType = "" // MavenDeploy
def appName = ""

def runPipeline(Map parameters) {
    pipeline {
            node {
                label 'master'

                stage("Echo Parameters") {
                  	print parameters
                  	print parameters.app
                  	appName = parameters.appName
                  	buildType = parameters.buildType
                  	deploymentType = parameters.deploymentType
                  	projectName = parameters.projectName
            	} 

                stage("Checkout") {
                  	checkout scm
            	} 

                stage("Build") {
                    buildType = getBuildType()
                    echo "BUILD TYPE"
                    echo buildType
                    if (buildType == "npm") {
                    	npmBuild()
                    } else if (buildType == "maven") {
                    	mavenBuild()
                    } else if (buildType == "MavenDeploy") {
                    	mavenDeploy()
                    } else if (buildType == "S2I") {
                    	echo "NO BUILD, DIRECT DOCKER BUILD"
                    } 
            	} 

            	stage ("Dockerize") {
            		echo "Docker"
            		if (deploymentType == "KieServer") {
            			echo "NO DOCKER BUILD"
            		} else {
            			buildAndPushDockerImage()
            		}
            		
            	}

            	stage ("Deploy") {
            		echo "Deploy"
            		if (deploymentType == "KieServer") {
            			deployToKieServer()
            		} else {
            			triggerDeploymentToOpenshift()
            		}
            		
            	}
            }
        }
} 

def npmBuild() {
	echo "NPM Build!"
	buildProfile = getBuildProfile()
	echo buildProfile

	sh """
		echo $buildProfile
		npm install
		if [ -z $buildProfile ];then
		   npm run build
		else
		   npm run build:$buildProfile
		fi
	"""

}

def mavenBuild() {
	echo "Maven Build!"
	sh """
		mvn clean install
	"""

}

def deployToKieServer() {
    groupId = readMavenPom().getGroupId()
    artifactId = readMavenPom().getArtifactId()
    version = readMavenPom().getVersion()        
	deployKjar = artifactId + '=' + groupId + ':' + artifactId + ':' + version
	echo "DEPLOY KJAR:"
	print deployKjar
}

def mavenDeploy() {
	echo "Maven Deploy!"
	configFileProvider([configFile(fileId: 'bpm-kie-build-settings', variable: 'MAVEN_SETTINGS')]) {
      sh 'mvn clean deploy -DskipTests -U -s $MAVEN_SETTINGS '
    }

}

def getBuildProfile() {
	def buildProfile = ""
	if (env.BRANCH_NAME == "dev" ) {
		buildProfile = ""
	} else if (env.BRANCH_NAME == "qa" ) {
		buildProfile = "qa"
	}
	return buildProfile
}

def getBuildType() {
	
	if (buildType == "") {
		if (fileExists('package.json')) {
	      buildType = "npm"
	    } else if (fileExists('pom.xml')) {
	      buildType = "maven"
	    }
	}

    return buildType
}

def buildAndPushDockerImage() {
	echo "############################"
	echo "Docker Build is started."
	echo "############################"

	imageDefinition = getImageDefinition()

	echo "Creating Image: "+ imageDefinition
	docker.withRegistry('https://aefes.azurecr.io', 'azure-container-registry') {
        def dockerImage = docker.build(imageDefinition)
		dockerImage.push()
	}

	echo "############################"
	echo "Docker Build is successful!"
	echo "############################"

	setGitTag()

}

def triggerDeploymentToOpenshift() {
	echo "############################"
	echo "Openshift Deployment is started."
	echo "############################"

	imageDefinition = getImageDefinition()
	namespace = getNameSpace()
	cluster = getCluster()

	node {
		withEnv(["PATH+OC=${tool 'oc'}"]) {
	        openshift.withCluster(cluster) {
	            openshift.withProject(namespace) {
	                def dcpatch = getDeploymentConfigPatch ()
	                echo dcpatch.toString()
	                openshift.apply(dcpatch)        
	            }
	        }
		}
	}
	

	echo "############################"
	echo "Openshift Deployment is successful."
	echo "############################"
}

def getImageDefinition(){
	def projectName = getProjectName()
	imageDefinition = projectName + "-" + env.BRANCH_NAME + ":" + env.BUILD_ID
	return imageDefinition
}

def getGitTag(){
	def projectName = getProjectName()
	gitTag = projectName + "-" + env.BRANCH_NAME + "-" + env.BUILD_ID
	return gitTag
}


def getProjectName () {
	def projectName = "${env.JOB_NAME}".split('/')[0]
	return projectName
}

def getNameSpace(){
	def namespace = ""
	if (env.BRANCH_NAME == "dev" ) {
		namespace = "bpm-dev"
	} else if (env.BRANCH_NAME == "qa" ) {
		namespace = "bpm-qa"
	}
	return namespace
}

def getCluster(){
	def cluster = ""
	
	if (env.BRANCH_NAME == "dev" || env.BRANCH_NAME == "qa") {
		cluster = "azure-dev-openshift"
	}

	return cluster
}

def getContainerRegistryAddress () {
	return "aefes.azurecr.io"
}

def setGitTag () {
	echo "Setting Git tag"
	
	gitTag = getGitTag()
	repositoryUrl = scm.userRemoteConfigs[0].url
	repositoryUrl = repositoryUrl.replace("https://", "")

	

	withCredentials([usernamePassword(credentialsId: "azure-repos-credentials", passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
		sh """
			echo $gitTag
			echo $repositoryUrl
			git tag $gitTag
			git push https://${GIT_USERNAME}:${GIT_PASSWORD}@$repositoryUrl --tags
		"""
     }
	
}

def getDeploymentConfigPatch () {

	def projectName = getProjectName()
	def namespace = getNameSpace()
	def imageRepositoryAddress = getContainerRegistryAddress() + "/" + getImageDefinition()

	def dcpatch = ["metadata":[
                       "name": projectName,
                       "namespace": namespace
                    ],
                   "apiVersion":"apps.openshift.io/v1",
                   "kind":"DeploymentConfig",
                   "spec":[
                       "template":[
                           "spec":[
                               "containers":[
                                     ["image": imageRepositoryAddress,
                                      "name": projectName
                                      ]
                               ]
                           ]
                       ]
                       ]
                   ];
    return dcpatch
}


return this