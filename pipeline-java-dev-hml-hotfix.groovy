def appGitBranch, appGitUrl

pipeline {
    agent {
        node { label 'maven' }   
    }
    options {
        timeout(time: 20, unit: 'MINUTES') 
    }
	stages {
		stage ('init pipeline') {
			steps {
				script {
					appGitBranch='main'
					appGitUrl='https://github.com/lennondsantos/todo-list-spring-boot.git'
				}
			}
		}
		stage ('clone') {
			steps {
				script {
					git credentialsId: env.GIT_CREDENTIALS, branch: appGitBranch, url: appGitUrl	
				}
			}
		}

	}
}
