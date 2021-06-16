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
		buildProfile = ""
	} else if (env.BRANCH_NAME == "qa" ) {
		buildProfile = "qa"
	} else if (env.BRANCH_NAME == "main" ) {
		buildProfile = "main"
	}
	return buildProfile
}

// Get NameSpace from BranchName
def GetNameSpaceFromBranch() {
	  def namespace = ""
	  if (env.BRANCH_NAME == 'main') {
	    namespace = "dev"
	  } else if (env.BRANCH_NAME == 'qa') {
	    namespace = "stage"
	  }
	  return namespace
}

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
			namespace = GetNameSpaceFromBranch()
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
