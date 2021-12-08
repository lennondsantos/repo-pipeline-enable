def appGitBranch, appGitUrl,pomFilePath = "",projectHomePath = ""

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
					git branch: appGitBranch, url: appGitUrl
					echo 'teste'
					sh 'pwd && ls -lha'
					
					pomFilePath = "/tmp/workspace/pipeline-spring/pom.xml"
					
				}
			}
		}
		stage ('test') {
			steps {
				script {
					sh "mvn test -P ${pomFilePath} -f ${pomFilePath}"
				}
			}
		}
	}
}
