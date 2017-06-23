# Overview

The Git plugin allows you to manage versions of Kettle files without leaving Spoon.
Local filesystem (whether or not connected as a File Repository) is supported, but neither Pentaho Repository nor Database Repository is.

# System requirements

It's been tested with PDI (CE) of 6.1, 7.0, and 7.1; but it should run on the corresponding version of EE.

# How to install

```
$ cd data-integration/plugins
$ unzip pdi-git-plugin-X.X.X-jar-with-dependencies.zip
```

To uninstall, just remove the `pdi-git-plugin` folder.

# How to use

The Git plugin is provided as a <i>perspective</i> (See [Using Perspectives](https://help.pentaho.com/Documentation/7.1/0L0/0Y0/020)).
Switch to the Git perspective

- Whenever you want when connected to a File Repository
- Only when a Transformation/Job is opened, saved at least once before, and focused when not connected to a File Repository

## Working with files

Stage changed files by drag & drop, write a good commit message, change the author name if necessary, and finally <b>Commit</b>.
Right-click on a commit pops up a context menu, where you can choose **checkout** to checkout that particular commit.

## Remote

**Tools > Git > Clone Repository** allows you to clone a remote repository to the local file system.
Currently only `origin` can be set and it is always the source of <b>Pull</b> and the target of <b>Push</b>.
<b>Pull</b> is equivalent of `git fetch; git merge --ff`.
If an error (e.g., merge conflict) happens, the operation will be just cancelled.

## Branches

Users can switch between branches.
Users can create / delete a branch.
Users can merge a branch into the current one.

## FAQ

### When pushing, I get an "UnknownHostKey" error

This happens when connecting to the remote repository via SSH.
Please add the host to `~/.ssh/known_hosts`

### The remote host has been added, but I still get an "UnknownHostKey" error

This could happen for example when the remote host is Gerrit.
The remote host will be added by executing `$ ssh -p 29418 hiromu@localhost`

```
[localhost]:29418 ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBAFgEc3BqPijHvVs5KoXLLoBaYtBlW8c8v+wpHEPpKObAF0lSG2qt764zFUE1eRlb/thq8RdNxHQ8l+i4VLTlR8=
```

However, JSch (Java library for SSH and used in the Git plugin) prefers **ssh-rsa** over **ecdsa-sha2-nistp256**. Hence, add the remote host like below.

```
[localhost]:29418 ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDVoiADWyjer2MRMZYAl1Ws/0zj9VyqMgYQxgNL+xcFGz4cO4AZIaL5L6TlNaU5bOF3WeCFgDLMrMioUoWS/0yLE5Q9mXwE2/5V3fEKDgMfuO+xvEGoh/xZb0GqhCeioG63+clqrXM8DvYfqzMmUg8ksPejEYeQpSrTkg0S5RE9AEB/+qvNnipye7M+9Nutr2lSE+GRhRfFNITCXLIAN6ukoKis+xVZgCMXFSnS41PlhQ/mLNJdA1bMxjm1/58iJsdF44iD+cuM/mFvLoAnXeAbOkkj8jyM136vAvO45M5c+a6Z8k4X7Q/CxsZ2IowWfUshg0jsjerzANUPCaoP9VJX
```

### When behind proxy

Define *System Properties* (not environment variables) like below in spoon.sh

```
OPT="$OPT -Dhttp.proxyHost=localhost -Dhttp.proxyPort=3128 -Dhttps.proxyHost=localhost -Dhttps.proxyPort=3128"
```

or in Spoon.bat

```
set OPT=%OPT% "-Dhttp.proxyHost=10.0.2.2" "-Dhttp.proxyPort=3128" "-Dhttps.proxyHost=10.0.2.2" "-Dhttps.proxyPort=3128"
```

The host and port should be replaced according to your proxy server.

### I got "407 Proxy Authentication Required" error

Proxy Authentication is currently not supported.

# How to compile

```
mvn clean package
```
