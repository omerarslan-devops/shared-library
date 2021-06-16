def projectName="" // test project
def buildType = "" // quarkusAppBuild, libraryBuild
def deploymentType = "" // MavenDeploy
def apptest = ""
def chartmuseum = ""
def uiTargetedEnv = ""

def runPipeline(Map parameters) {
    pipeline {
            node {
                label 'main'

                stage("Echo Parameters") {
                  	print parameters
                  	apptest = parameters.apptest
                  	buildType = parameters.buildType
                  	deploymentType = parameters.deploymentType
                  	projectName = parameters.projectName
            	} 
	    }
    }
}
return this
