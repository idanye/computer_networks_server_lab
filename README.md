
# README

This file explains what exactly we have implemented. Explains each class with a few
words, and its role in the program. 
Also included a paragraph on the design we have chosen to implement our server.

## Classes

### `MultiThreadedServer.java`

In this file we have 2 classes:
## MultiThreadedServer class
This is the class of that starts the server and initializes the threadpool.

## ClientHandler class
This class is responsible for getting client sockets and handling the types of requests the server supports.

### `HTTPRequest.java`

## HTTPRequest class
We used the recommendation of the lab and implemented this class which represents an HTTP request. It's responsible for the parsing of the requests and we use it as a data structure that other parts of the program use to client requests.

### `ServerConfig.java`

## ServerConfig class
This class is responsible for loading and storing server configuration from the config.ini file. 
It has getters which allow the program to get the data from the config file and use it in the program.

### `Util.java`

## Util class
We wrote some utility functions in this file that we use in our program like methods for converting strings to bytes and char arrays to bytes etc..
Since we used these functions in many places in the code we put them in this class to reduce code duplications.

### `BadRequestException.java`

## BadRequestException
This class is just a custom exception that we throw when we get a bad HTTP request from the client. we use it in the server to handle situations where a request cannot be parsed or processed correctly.


## Design

Our server is designed with a multi-threaded architecture using Executors library (like we saw in the socket tutorial). We used a fixed thread-pool to handle multiple client connections simultaneously. This design helps to prevent exhaustion and ensure our server won't crush under load.

We chose to separate the code into several classes and each of them handles different part of the server function. This separation made our code more readable and also helped us to reduce code duplication in places we needed to use the same functions. It also when we had error since we knew from which part of the code we got the error and then we could debug and fix them much faster.

