# ktor-encryption-server

The ktor-encryption-server project provides functionality for secure storage and transfer of files. For accessing the functionality a REST API is available.

Current version 0.0.1 contains the following functionality:


## Add new user

This request is adding a new user with given username and password. Additionally a custom expiration period for upload files is specified.
The username must be unique accross all users.

## Upload files

This request is uploading one or multiple files. Only authenticated user are able to execute this request. The result contains a list of upload files consist of original 
filname, the encrypted filename, the password for accessing upload and a link for downloading the file and a link for deleting the file.

## Download files

This request downloads a single file.

## Get uploads

This request returns a list of all active uploads of the user. Uploads which are already removed from server, because they are expired, are not part of the list.

## Delete upload file

This request deletes an existing upload file from server. This also removed the history for this file.

## Delete user

This request deletes a user from server. This also removes all uploaded files of the user.

## Get upload history

This request returns the upload history of the user. The history contains the upload and all the downloads of a file.

## Update user settings

This request updates the user settings.


Sample requests can be found in a postman collection located in /postman/KtorEncryptionServer.postman_collection.json
