# ktor-encryption-server

The ktor-encryption-server project provides functionality for secure storage and transfer of files. For accessing the functionality a REST API is available.

Current version 0.0.1 contains the following functionality:

## General functionality
The ktor-encryption-server solves the following use-case. User A wants to share file(s) with one or more
other users, but want to store that file in accessible format on some cloud storage (nobody knows
if google is able to access my files). So user A uploads the file(s) to the server. The server encrypts every
file during upload and stores it using a random unique name. Also the checksum of the file is stored. When
the upload is finished the server returns the random filename + a secure password for every file. With
this information every person, can download the file. Only the provider of the file needs to be 
registered. If a download request is hitting the server, the file is decrypted again. Before the result is returned, 
the checksum of the stored file is verified. With this it is not possible to. Change the encrypted file content.
User A can check the download history of every file, as long as the files are stored. After a predefined period
of time, the files are automatically removed from server. No information remains. User A also had the possibility
to manually remove the file before. 

## Encryption 
For encryption of user password and files, which are uploaded, a symetric encryption algorithm,  AES 256 is used. Currently this algorithm
is treated as secure (see e.g. https://www.clickssl.net/blog/256-bit-encryption). For AES there are different modes available. ktor-encryption-server
uses  GCM (see https://csrc.nist.rip/groups/ST/toolkit/BCM/documents/proposedmodes/gcm/gcm-spec.pdf&ved=2ahUKEwjfgvOrnuX6AhVnwAIHHVoiB34QFnoECEsQAQ&usg=AOvVaw1_5XL9BOHPgCHR9qPGEI0I).

The parameters for the encryption process can be configured by application settings.

TBD

## Password Security
The password security is active for user password and the random password for file upload.
There are following requirements (which are fix at the moment):
- minimum 16 characters
- minimum one uppercase character
- minimum one lowercase character
- minimum one digit
- minimum one special character

## Requests

### Add new user

This request is adding a new user with given username and password. Additionally a custom expiration period for upload files can be specified.
The username must be unique accross all users and the password musst fulfill the mentioned
requirements.

## Upload files (authenticated)

This request is uploading one or multiple files. Only authenticated user are able to execute this request. The result contains a list of upload files consist of original 
filname, the random storage filename, the password for accessing upload and a link for downloading the file and a link for deleting the file.

## Download files

This request downloads a single file, specifying the storage filename and the password.

## Get uploads (authenticated)

This request returns a list of all active uploads of the user. Uploads which are already removed from server, because they are expired or deleted, 
are not part of the list.

## Delete upload file (authenticated)

This request deletes an existing upload file from server. This also removes the history for this file.

## Delete user (authenticated)

This request deletes a user from server. This also removes all uploaded files of the user.

## Get upload history (authenticated)

This request returns the upload history of the user. The history contains the upload and all the downloads of a file.

## Update user settings (authenticated)

This request updates the user settings.


Sample requests can be found in a postman collection located in /postman/KtorEncryptionServer.postman_collection.json
