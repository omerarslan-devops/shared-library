def projectName="" // test project
def buildType = "" // quarkusAppBuild, libraryBuild
def deploymentType = "" // MavenDeploy
def appName = ""
def chartmuseum = ""
def uiTargetedEnv = ""
def newparameter01 = ""
def newparameter02 = ""

def runPipeline(Map parameters) {
    pipeline {
            node {
                label 'main'

                stage("Echo Parameters") {
                  	print parameters
                  	appName = parameters.appName
                  	buildType = parameters.buildType
                  	deploymentType = parameters.deploymentType
                  	projectName = parameters.projectName
			parametremiz01 = parameters.newparameter01
			parametremiz02 = parameters.newparameter02
			echo appName
			echo buildType
			echo parametremiz01
			echo parametremiz02
            	} 
	    }
    }
}
return this
