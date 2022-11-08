# ktor-encryption-server

The **ktor-encryption-server** project provides functionality for secure storage and transfer of files. For accessing
the functionality a REST API is available.

Current version 0.0.1 contains the following functionality:

## General functionality

The **ktor-encryption-server** solves the following use-case. An user wants to share file(s) with one or more
other users, but want to store that file in accessible format on some cloud storage (nobody knows
if Google is able to access my files). So the user uploads the file(s) to the server. The server encrypts every
file during upload and stores it using a random unique name. Also, the checksum of the file is stored. When
the upload is finished the server returns the random filename and a secure password for every file. With
this information every person, can download the file. Only the provider of the file needs to be
registered. If a download request is hitting the server, the file is decrypted again. Before the result is returned,
the checksum of the stored file is verified. With this it is not possible to. Change the encrypted file content.
The user can check the download history of every file, as long as the files are stored. After a predefined period
of time, the files are automatically removed from server. No information remains. The user also had the possibility
to manually remove the file before.

## Encryption

For encryption of user password and files, which are uploaded, a symetric encryption algorithm, AES 256 is used.
Currently this algorithm
is treated as secure (see e.g. [AES Encryption]([https://www.clickssl.net/blog/256-bit-encryption)). For AES there are
different modes available. ktor-encryption-server
uses GCM (
see [GCM](https://csrc.nist.rip/groups/ST/toolkit/BCM/documents/proposedmodes/gcm/gcm-spec.pdf&ved=2ahUKEwjfgvOrnuX6AhVnwAIHHVoiB34QFnoECEsQAQ&usg=AOvVaw1_5XL9BOHPgCHR9qPGEI0I))
.

The parameters for the encryption process can be configured by application
settings ([application.conf](src/main/resources/application.conf)). Below
you can see the default values.

```
 security {
        fileIntegrityCheckHashingAlgorithm = "SHA-512"
        defaultPasswordKeySizeBytes = 256
        defaultNonceLengthBytes = 32
        defaultSaltLengthBytes = 64
        defaultIterationCount = 10000
        defaultGcmParameterSpecLength = 128
    }
 ```

## Password Security

The password security is active for user password and the random password for file upload.
There are following requirements.

- minimum 16 characters
- minimum one uppercase character
- minimum one lowercase character
- minimum one digit
- minimum one special character

The password settings can be customized by application.conf.

```
    passwordSettings{
        minimumLength = 16
        mustContainDigits = true
        mustContainUpperCase = true
        mustContainLowerCase = true
        mustContainSpecial = true
    }
```

## Database Configuration

By default, a postgresql database used for persistence. This can easily be replaced by other vendor. The configuration
of the database is
done in application settings ([application.conf](src/main/resources/application.conf))

```
    database {
        driverClass = "org.postgresql.Driver"
        url = "jdbc:postgresql://db:5432/db"
        user = "user"
        password = "password"
        maxPoolSize = 10
    }
```

The postgresql database can be started using the [docker-compose.yml](docker-compose.yml) file.

## Upload configuration

There are three settings available for customization:

```
    uploadSettings{
        directoryPath = "./uploads"
        expirationDays = 6
        uploadMaxSizeInMb = 2000
        validMimeTypes = ["application/pdf", "text/plain", "application/octet-stream"]
    }
```

- The path for storing the upload files (application need to have write access).
- The default expiration days fur upload files.
- The upload limit in MB.
- The valid mime types that are accepted for upload. For detection of mime type Apache Tika is used.

## Requests

### Add new user (POST: /api/user)

This request is adding a new user with given username and password. Additionally a custom expiration period for upload
files can be specified.
The username must be unique accross all users and the password musst fulfill the mentioned
requirements.

### Upload files (authenticated: POST: /api/upload)

This request is uploading one or multiple files. Only authenticated user are able to execute this request. The result
contains a list of upload files consist of original
filname, the random storage filename, the password for accessing upload and a link for downloading the file and a link
for deleting the file.

### Download files (GET: /api/download)

This request downloads a single file, specifying the storage filename and the password.

### Get uploads (authenticated: GET: /api/upload)

This request returns a list of all active uploads of the user. Uploads which are already removed from server, because
they are expired or deleted,
are not part of the list.

### Delete upload file (authenticated: DELETE: /api/upload)

This request deletes an existing upload file from server. This also removes the history for this file.

### Delete user (authenticated: DELETE /api/user)

This request deletes a user from server. This also removes all uploaded files of the user.

### Get upload history (authenticated: GET: /api/upload/history)

This request returns the upload history of the user. The history contains the upload and all the downloads of a file.

### Update user settings (authenticated: PUT: /api/user/settings)

This request updates the user settings.

### Update user password (authenticated: PUT: /api/user/password)

This request updates the user password.

Sample requests can be found in a postman collection located
in [Postman Collection](postman/KtorEncryptionServer.postman_collection.json)

## Run ktor-encryption-server

The **ktor-encryption-server** can be run in docker using the [Dockerfile](Dockerfile) or directly use the
[docker-compose.yml](docker-compose.yml) to start the application together with the postgresql container.

## Next

The next topics I will work on:

- Add OpenAPI specification
- Optimize serving download content (not storing decrypted version on server until request is finished but directly
  streaming to consumer)
- Add client for better usability