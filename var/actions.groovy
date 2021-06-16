def projectName="" // BPM, HIP or E-SALES
def buildType = "" // quarkusAppBuild, libraryBuild
def deploymentType = "" // MavenDeploy
def appName = ""
def chartmuseum = 'http://chartmuseum-default.apps.r7mikab1.westeurope.aroapp.io'
def uiTargetedEnv = ""

def runPipeline(Map parameters) {
    pipeline {
            node {
                label 'master'

                stage("Echo Parameters") {
                  	print parameters
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
                    echo "BUILD TYPE:"
                    echo buildType
                    if (buildType == "npm") {
                    	npmBuild()
                    } else if (buildType == "maven") {
                    	mavenBuild()
                    } else if (buildType == "MavenDeploy") {
                    	mavenDeploy()
                    } else if (buildType == "LibDeploy") {
                    	libDeploy()
                    }  else if (buildType == "S2I") {
                    	echo "NO BUILD, DIRECT DOCKER BUILD"
                    } else if (buildType == "quarkus") {
                    	quarkusBuild()
                    } 
            	} 

            	stage ("Dockerize") {
            		echo "Docker"
            		if (deploymentType == "KieServer" || buildType == "LibDeploy") {
            			echo "NO DOCKER BUILD"
            		} else {
            			buildAndPushDockerImage()
            		}
            		
            	}

            	stage ("Deploy") {
            		echo "Deploy"
					
            		if (deploymentType == "KieServer" || buildType == "LibDeploy") {
            			//deployToKieServer()
						echo "NO DEPLOYMENT"
            		} else {
            			triggerDeploymentToOpenshift()
            		}
            		
            	}
            }
        }
} 

def runMicroservicePipeline(Map parameters) {
    pipeline {
            node {
                label 'master'

                stage("Echo Parameters") {
                  	print parameters
                  	appName = parameters.appName
                  	buildType = parameters.buildType
            	} 

                stage("Checkout") {
                  	checkout scm
            	} 

                stage("Build") {
                	echo "BUILD TYPE:"
                    echo buildType
                    if (buildType == "libraryBuild") {
                    	libDeploy()
                    } else if (buildType == "quarkusAppBuild") {
                    	quarkusBuild()
                    }  else if (buildType == "gradleBuild") {
                    	gradleBuild()
                    }  else if (buildType == "gradleCommonBuild") {
                    	gradleCommonBuild()
                    } 

            	} 

            	stage ("Dockerize") {
            		echo "Docker"
            		if ( buildType == "libraryBuild") {
            			echo "NO DOCKER BUILD"
            		} else  if (buildType == "gradleCommonBuild") {
            			echo "NO DOCKER BUILD"
            		} else  if (appName.contains("e-sales")) {
            			buildAndPushDockerImageWithArgs()
            		}  else {
            			buildAndPushDockerImage()	
            		}
            		
            	}

            	stage ("Update Helm Chart") {
            		echo "Helm Chart Updating"
					echo appName
            		if ( buildType == "libraryBuild") {
						echo "NO DEPLOYMENT"
            		} else if ( buildType == "gradleCommonBuild") {
						echo "NO DEPLOYMENT"
            		} else if ( appName.contains("e-sales")) {
						updateHelmChartImageVersionEsales()
            		}  else {
            			updateHelmChartImageVersion()
            		}
            		
            	}

            	stage ("Helm Chart Upgrade") {
            		echo "Helm Chart Dry Run"
            		if (buildType == "libraryBuild") {
						echo "NO DEPLOYMENT"
            		} else if ( buildType == "gradleCommonBuild") {
						echo "NO DEPLOYMENT"
            		} else if ( appName.contains("e-sales")) {
						startHelmChartDeploymentEsales()
            		}   else {
            			startHelmChartDeployment()
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

def libDeploy() {
	echo "Maven Deploy!"
	env.JAVA_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
	echo sh(script: 'env|sort', returnStdout: true)
	configFileProvider([configFile(fileId: '74a441a1-3edb-44c6-8990-c7dbbfdf8634', variable: 'MAVEN_SETTINGS')]) {
      sh 'mvn deploy -Dmaven.compiler.executable=/usr/lib/jvm/java-1.11.0-openjdk-amd64 -DskipTests -U -s $MAVEN_SETTINGS '
    }
}

def quarkusBuild() {
	echo "Quarkus  Build!"
	sh 'chmod 775 mvnw'
	configFileProvider([configFile(fileId: '74a441a1-3edb-44c6-8990-c7dbbfdf8634', variable: 'MAVEN_SETTINGS')]) {
      sh './mvnw package -U -s $MAVEN_SETTINGS -DskipTests '
    }
}

def gradleBuild() {
	echo "Gradle  Build!"
	if (appName == "e-sales-user") {
		sh 'bash ./gradlew -Dorg.gradle.java.home=/usr/lib/jvm/java-11-openjdk-amd64 -p e-sales-user clean compileJava bootJar'
	} else if (appName == "e-sales-promotion")  {
		sh 'bash ./gradlew -Dorg.gradle.java.home=/usr/lib/jvm/java-11-openjdk-amd64 -p e-sales-promotion clean compileJava bootJar'
	} else if (appName == "e-sales-ui")  {
		sh 'bash ./gradlew :ui:build -x test'
	}

	

	echo "Gradle Build Finish"
	
}

def gradleCommonBuild() {
	echo "Gradle  Build!"
	if (appName == "e-sales-common")  {
		sh 'bash ./gradlew -p e-sales-common clean compileJava jar publish'
	}

	echo "Gradle Common Build Finish"
	
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


def buildAndPushDockerImageWithArgs() {
	echo "############################"
	echo "Docker Build is started."
	echo "############################"

	imageDefinition = getImageDefinition()

	echo "Creating Image: "+ imageDefinition
	def dockerBuildArgs = ""

	if (appName == "e-sales-user") {
		dockerBuildArgs = "./e-sales-user"
	} else if (appName == "e-sales-promotion") {
		dockerBuildArgs = "./e-sales-promotion"
	} else if (appName == "e-sales-ui") {
		uiTargetedEnv = getUIEnvFromBranch ()
		echo uiTargetedEnv
		dockerBuildArgs = "--build-arg DEV_ENVIRONMENT=$uiTargetedEnv ./e-sales-ui"
	}
	
	docker.withRegistry('https://aefes.azurecr.io', 'azure-container-registry') {
        def dockerImage = docker.build(imageDefinition, "$dockerBuildArgs")
		dockerImage.push()
	}

	echo "############################"
	echo "Docker Build is successful!"
	echo "############################"

	setGitTag()
}

def getUIEnvFromBranch() {
  if (env.BRANCH_NAME == 'dev') {
    uiTargetedEnv = "dev"
  } else if (env.BRANCH_NAME == 'qa') {
    uiTargetedEnv = "stage"
  }
  return uiTargetedEnv
}

def quarkusNativeDockerBuild() {
	echo "############################"
	echo "Quarkus Native Docker Build is started."
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

def getGitTag(){
	def projectName = getProjectName()
	gitTag = projectName + "-" + env.BRANCH_NAME + "-" + env.BUILD_ID
	return gitTag
}


def triggerDeploymentToOpenshift() {
	echo "############################"
	echo "Openshift Deployment is started."
	echo "############################"

	imageDefinition = getImageDefinition()
	namespace = getNameSpaceBPM()
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

def getProjectName () {
	def projectName = "${env.JOB_NAME}".split('/')[0]
	return projectName
}

def getNameSpace(){
	def namespace = ""
	if (appName == ""){
		if (env.BRANCH_NAME == "dev" ) {
		namespace = "bpm-dev"
		} else if (env.BRANCH_NAME == "qa" ) {
			namespace = "bpm-qa"
		}
	} else {
	if (projectName == "efes-connect"){
		if (env.BRANCH_NAME == "dev" ) {
		namespace = "efes-connect-dev"
		} else if (env.BRANCH_NAME == "qa" ) {
			namespace = "efes-connect-qa"
		}

	} else if (appName.contains ("e-sales")) {
		if (env.BRANCH_NAME == "dev" ) {
			namespace = "e-sales-helm-test"
		} else if (env.BRANCH_NAME == "qa" ) {
			namespace = "e-sales-qaASF"
		}
	} else {
		if (env.BRANCH_NAME == "dev" ) {
		namespace = "bpm-dev"
		} else if (env.BRANCH_NAME == "qa" ) {
			namespace = "bpm-qa"
		}
	}
	}
	
	return namespace
}

def getNameSpaceBPM(){
	def namespace = ""
		if (env.BRANCH_NAME == "dev" ) {
		namespace = "bpm-dev"
		} else if (env.BRANCH_NAME == "qa" ) {
			namespace = "bpm-qa"
		}

	
	return namespace
}

def getHelmChartName(){
	def helmChartName = ""
	if (appName.contains ("e-sales")){
		helmChartName = "e-sales"
	}

	
	return helmChartName
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

def getDeploymentConfigPatch () {

	def projectName = getProjectName()
	def namespace = getNameSpaceBPM()
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

def updateHelmChartImageVersionEsales() {
					echo "updateHelmChartImageVersionEsales"
                  	dir('helm-charts') {
				    	git branch: env.BRANCH_NAME, url: 'https://dev.azure.com/anadoluefes/infrastructure/_git/helm-charts', credentialsId: 'azure-repos-credentials'
						if (appName.contains ("e-sales")) {
							echo "e-sales"
							def imageVersionsYaml = readYaml file: './e-sales/values.yaml'
							def chartVersionsYaml = readYaml file: './e-sales/Chart.yaml'

							def chartAppName = ""
							if (appName == "e-sales-ui") {
								chartAppName = "esalesui"
							} else if (appName == "e-sales-user") {
								chartAppName = "eSalesUser"
							} else if (appName == "e-sales-promotion") {
								chartAppName = "eSalesPromotion"
							}
							print chartAppName
							print imageVersionsYaml
							def appVersion = env.BUILD_ID
							imageVersionsYaml[chartAppName].deployment.version = appVersion
							
							print imageVersionsYaml
							print appName

							chartVersion = chartVersionsYaml.version 
							chartVersionsYaml.version = chartVersion + 1

							writeYaml file: './e-sales/values.yaml', data: imageVersionsYaml, overwrite: true
							writeYaml file: './e-sales/Chart.yaml', data: chartVersionsYaml, overwrite: true
							withCredentials([usernamePassword(credentialsId: 'azure-repos-credentials', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
	                        	sh """
	                        	
	                        	git add .
	                        	git commit -m "Helm chart updated"
	                        	git push https://${GIT_USERNAME}:${GIT_PASSWORD}@dev.azure.com/anadoluefes/infrastructure/_git/helm-charts

	                        	"""
	                   		}

	                   		script {
	                   			sh """
	                   			helm package ./e-sales
	                   			helm push *.tgz http://chartmuseum-default.apps.r7mikab1.westeurope.aroapp.io 
	                   			rm *.tgz
	                   			"""
	                   		}

						}

						
					}
} 

def updateHelmChartImageVersion() {

                  	dir('helm-charts') {
				    	git branch: "dev", url: 'https://dev.azure.com/anadoluefes/infrastructure/_git/helm-charts', credentialsId: 'azure-repos-credentials'
						
						def imageVersionsYaml = readYaml file: './efes-connect-umbrella-chart/values.yaml'
						def chartVersionsYaml = readYaml file: './efes-connect-umbrella-chart/Chart.yaml'
						def appVersion = env.BUILD_ID
						print imageVersionsYaml
						print appName
						imageVersionsYaml[appName].deployment.version = appVersion
						chartVersion = chartVersionsYaml.version 
						chartVersionsYaml.version = chartVersion + 1
						writeYaml file: './efes-connect-umbrella-chart/values.yaml', data: imageVersionsYaml, overwrite: true
						writeYaml file: './efes-connect-umbrella-chart/Chart.yaml', data: chartVersionsYaml, overwrite: true

						withCredentials([usernamePassword(credentialsId: 'azure-repos-credentials', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                        	sh """
                        	
                        	git add .
                        	git commit -m "Helm chart updated"
                        	git push https://${GIT_USERNAME}:${GIT_PASSWORD}@dev.azure.com/anadoluefes/infrastructure/_git/helm-charts

                        	"""
                   		}

                   		script {
                   			sh """
                   			
                   			helm dependency update ./efes-connect-umbrella-chart
                   			helm package ./efes-connect-umbrella-chart
                   			helm push *.tgz http://chartmuseum-default.apps.r7mikab1.westeurope.aroapp.io 
                   			rm *.tgz
                   			"""
                   		}
					}
} 



def helmChartDryRun () {

	withEnv(["PATH+OC=${tool 'oc'}"]) {
                            withCredentials([string(credentialsId: 'aro-dev-generic', variable: '_TOKEN')]) {
                                sh """
                                oc login --token=${_TOKEN}
                            """
                            }
                        }

	dir('helm-charts') {
		sh """
		pwd
		ls
		

		oc rollout status dc ec-gateway-authentication --namespace helm-test
		## helm upgrade -i --debug --dry-run --namespace 1.0.0 eisd-dev ./efes-connect-umbrella-chart
		## helm install --debug --dry-run --generate-name ./mychart
		## helm upgrade -i ec-dev-chart ./efes-connect-umbrella-chart --namespace efes-connect-dev --wait --timeout 1m1s 
		"""
	}       	
} 

def startHelmChartDeployment (parameters) {	
	    def deployHelmChartJob = build job: 'helm-deploy-test', wait: true, parameters: [[$class: 'StringParameterValue', name: 'appName', value: appName]]
	    print "Deployment Success"
} 

def startHelmChartDeploymentEsales (parameters) {	
		echo "startHelmChartDeploymentEsales"
		helmNameSpace = getNameSpace ()
		helmChartName = getHelmChartName ()
	    def deployHelmChartJob = build job: 'helm-deploy', wait: true, parameters: [[$class: 'StringParameterValue', name: 'appName', value: appName], [$class: 'StringParameterValue', name: 'namespace', value: helmNameSpace], [$class: 'StringParameterValue', name: 'chartName', value: helmChartName]]

	    print "Deployment Success"
} 


def deployHelmChart(Map parameters) {
    pipeline {
            node {
                label 'master'

                stage("Echo Parameters") {
                	print parameters.appName
                	withEnv(["PATH+OC=${tool 'oc'}"]) {
                        withCredentials([string(credentialsId: 'aro-dev-generic', variable: '_TOKEN')]) {
                            sh """
                            oc login --token=${_TOKEN}
                            helm repo add chartmuseum http://chartmuseum-default.apps.r7mikab1.westeurope.aroapp.io/
							helm repo update
							helm upgrade -i ec-dev-chart chartmuseum/efes-connect-umbrella-chart --namespace efes-connect-dev  --wait --timeout 1m1s 
                            oc rollout status dc ${parameters.appName} --namespace efes-connect-dev
                        	"""
                        }
                     }
                  	
            	} 

            }
        }
}

def deployHelmChartEsales(Map parameters) {
    pipeline {
            node {
                label 'master'

                stage("Echo Parameters") {
                	print parameters
                	print parameters.appName
                	print parameters.chartName
                	print parameters.namespace

                	withEnv(["PATH+OC=${tool 'oc'}"]) {
                        withCredentials([string(credentialsId: 'aro-dev-generic', variable: '_TOKEN')]) {
                            sh """
                            oc login --token=${_TOKEN}
                            helm repo add chartmuseum http://chartmuseum-default.apps.r7mikab1.westeurope.aroapp.io/
							helm repo update
							helm upgrade -i ${parameters.chartName} chartmuseum/${parameters.chartName} --namespace ${parameters.namespace}  --wait --timeout 1m1s 
                            oc rollout status dc ${parameters.appName} --namespace ${parameters.namespace} 
                        	"""
                        }
                     }
                  	
            	} 

            }
        }
}

return this