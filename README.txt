# ChatOS

ChatOS is a chatting software produce in java composed by the client part and the server part.
Developed by :
CHARAMOND Lucien, mail : lchara01@etud.u-pem.fr 
PORTEFAIX Maxime, mail : mportefa@etud.u-pem.fr


## Project distribution

Folder src : code sources with packages : 
	- `fr.upem.net.chatos` for implementations : client, frame, reader, server.
	- `fr.upem.net.test.chatos` for JUnit tests.
	
Folder doc : ChatOS project documentation :
	- JDOC : the code javadoc
	- RFC : the description of the ChatOS protocol
	- + Report and User Manual  
	
Folder jar : Jar files location pre-build to launch the client and the server

Folder ant-jar : Jar files location build by ant to launch the client and the server

Folder TCPfiles : TCP files registering location


## Installation

At the project root use the ant build :
`ant build-jar`


## Usage

To launch a ChatOS server, please launch the following command in the jar folder or in the resources folder :
`java -jar ChatOsServer.jar [port]`

To launch a ChatOS client, please launch the following command in the jar folder or in the resources folder :
`java -jar ChatOsClient.jar [pseudonym] [host] [port]`

How to use ChatOS : 
- Send a public message : `message`
- Send a private message : `@TargetPseudonym message`
- Establish TCP private connexion : `/TargetPseudonym filePath` //, The recipient should answer with `y` or `n`
