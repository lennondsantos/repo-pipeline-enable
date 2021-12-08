pipeline {
    agent {
        node { label 'maven' }   
    }
    options {
        timeout(time: 20, unit: 'MINUTES') 
    }
	stages {
		stage ('clone') {
			steps {
				git credentialsId: env.GIT_CREDENTIALS, branch: appGitBranch, url: appGitUrl

			}
		}

	}
}
