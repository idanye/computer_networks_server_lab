
# README

## Overview

This document outlines the implementation and design of a multi-threaded HTTP server written in Java. The server is capable of handling HTTP requests, parsing and responding accordingly, and is designed with modularity and extensibility in mind.

## Classes

### `MultiThreadedServer.java`
This is the main class of the server that initializes and starts the server. It listens for incoming connections and handles them using a fixed thread pool to manage concurrent requests. This class is responsible for accepting client sockets and dispatching them to worker threads for processing, ensuring scalability and efficient resource utilization.

### `HTTPRequest.java`
This class represents an HTTP request. It is responsible for parsing the incoming request from the client, extracting important information such as the HTTP method, URI, and headers. It acts as a data structure that other parts of the program use to understand and respond to client requests.

### `ServerConfig.java`
This class is responsible for loading and storing server configuration from a properties file. It includes settings such as the server port, root directory, and maximum number of threads. This design allows for easy adjustments to the server's configuration without modifying the source code.

### `Util.java`
A utility class that provides common utility functions used across the server, such as methods for converting strings to bytes and handling file MIME types. This class helps in reducing code duplication and improving maintainability by centralizing common functionalities.

### `BadRequestException.java`
This class defines a custom exception that is thrown to indicate a bad or malformed HTTP request from the client. It is used within the server to handle situations where a request cannot be parsed or processed correctly, allowing for graceful error handling and response to the client. This ensures that the server can inform the client about the nature of the error, maintaining robust communication even in error scenarios.


## Design

The server is designed with a multi-threaded architecture to efficiently handle multiple client connections simultaneously. By utilizing a thread pool, the server can limit the number of concurrent threads, preventing resource exhaustion and ensuring stable performance under load.

The separation of concerns is a key aspect of the server's design. Each class has a distinct responsibility, from handling HTTP requests and server configuration to providing utility functions. This modular approach not only makes the codebase more readable and maintainable but also facilitates future enhancements and feature additions.

The server's design is focused on simplicity and functionality, providing a solid foundation for further development and customization. It demonstrates key principles of network programming in Java, such as socket programming, thread management, and I/O handling.
