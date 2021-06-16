//def projectName="" // test project
def buildType = "" // quarkusAppBuild, libraryBuild
def deploymentType = "" // MavenDeploy
def appName = ""
def chartmuseum = ""
def uiTargetedEnv = ""
def newparameter01 = ""
def newparameter02 = ""

// Get Image Definition Method
def getImageDefinition(){
	def projectName = getProjectName()
	imageDefinition = projectName + "-" + env.BRANCH_NAME + ":" + env.BUILD_ID
	return imageDefinition
}

// Get Project Name Method
def getProjectName () {
	def projectName = "${env.JOB_NAME}".split('/')[0]
	return projectName
}

// Get Cluster 
def getCluster(){
	def cluster = ""
	
	if (env.BRANCH_NAME == "dev" || env.BRANCH_NAME == "qa") {
		cluster = "azure-dev-openshift"
	} else if (env.BRANCH_NAME == "main" ) {
		cluster = "benim-clusterim"
	}
	return cluster
}

// Get GitTag
def getGitTag(){
	def projectName = getProjectName()
	gitTag = projectName + "-" + env.BRANCH_NAME + "-" + env.BUILD_ID
	return gitTag
}

// Get Build Profile
def getBuildProfile() {
	def buildProfile = ""
	if (env.BRANCH_NAME == "dev" ) {
		buildProfile = "dev"
	} else if (env.BRANCH_NAME == "qa" ) {
		buildProfile = "qa"
	} else if (env.BRANCH_NAME == "main" ) {
		buildProfile = "main"
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

// Get NameSpace from BranchName
def getNameSpace(){
	def namespace = ""
	if (appName == ""){
		if (env.BRANCH_NAME == "dev" ) {
		namespace = "bpm-dev"
		} else if (env.BRANCH_NAME == "qa" ) {
			namespace = "bpm-qa"
		}
	} else {
	if (projectName == "connect"){
		if (env.BRANCH_NAME == "dev" ) {
		namespace = "connect-dev"
		} else if (env.BRANCH_NAME == "qa" ) {
			namespace = "connect-qa"
		}

	} else if (appName.contains ("e-sales")) {
		if (env.BRANCH_NAME == "dev" ) {
			namespace = "e-sales-dev"
		} else if (env.BRANCH_NAME == "qa" ) {
			namespace = "e-sales-qa"
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

def getContainerRegistryAddress () {
	return "aefes.azurecr.io"
}

def getHelmChartName(){
	def helmChartName = ""
	if (appName.contains ("e-sales")){
		helmChartName = "e-sales"
	}
	
	return helmChartName
}
///////////////// Build Methods ///////////////////////////////////

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

//////////////////////////////////////////////////////
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

///////////////////////////////////////////////

def runPipeline(Map parameters) {
    pipeline {
            node {
                label 'main'

                stage("Echo Parameters") {
                  	print parameters
                  	appName = parameters.appName
                  	buildType = parameters.buildType
                  	deploymentType = parameters.deploymentType
                  	//projectName = parameters.projectName
			parametremiz01 = parameters.newparameter01
			parametremiz02 = parameters.newparameter02
			namespace = getNameSpace()
			echo appName
			echo buildType
			echo parametremiz01
			echo parametremiz02	
			echo buildProfile
			echo gitTag
			echo cluster
			echo projectName
			echo imageDefinition
			echo namespace
            	}
            }
        }
} 
return this
