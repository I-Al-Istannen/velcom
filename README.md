# VelCom

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [VelCom](#velcom)
    - [Installation guide](#installation-guide)
    - [Starting VelCom](#starting-velcom)
    - [Configuring the web server](#configuring-the-web-server)

<!-- markdown-toc end -->

## Installation guide

Ensure that the following tools are installed on your system:
- `make`
- `maven`
- `yarn`

Clone the repo and use `make` to build the backend and frontend.

```
$ git clone velcom-repo-url
$ cd velcom
$ make
```

Copy the `backend.jar` and `example_config.yml` files to the location where the
backend should run, modify the config file and start the backend. These commands
assume you are in the base directory of the `velcom` repo.

```
$ cp backend/backend/target/backend.jar install/dir/
$ cp backend/backend/src/main/resources/example_config.yml install/dir/config.yml
$ vi install/dir/config.yml
```

Copy the `runner.jar` and `example_config.json` files to the location where the
runner should run, modify the config file and start the runner. These commands
assume you are in the base directory of the `velcom` repo.

```
$ cp backend/runner/target/runner.jar install/dir/
$ cp backend/runner/src/main/resources/example_config.json install/dir/config.json
$ vi install/dir/config.json
```

Copy the frontend `dist` directory to a location where it will be served by a
web server and add the impressum. For more details on how the impressum can be
formatted, visit the frontend's "About" tab before adding the impressum. For
more details on how the web server needs to be configured, see the the section
on [configuring the web server](#configuring-the-web-server) below.

<!-- TODO: What about the server URL? -->
<!-- TODO: Instructions on how to set up nginx/how the webserver needs to behave -->

```
$ cp -r frontend/dist/ path/to/install/dir/
$ vi path/to/install/dir/dist/Impressum.html
```

## Starting VelCom

In the directory where the backend was installed, run the `backend.jar`. The
first argument should be `server` and the second argument the path to the config
file. If you followed the installation steps above, this looks like:

```
$ java -jar backend.jar server config.yml
```

In the directory where the runner was installed, run the `runner.jar`. The first
(and only) argument should be the path to the config file. If you've followed
the installation steps above, this looks like:

```
$ java -jar runner.jar config.json
```

Ensure your web server is running and serving the `dist/` directory correctly.

## Configuring the web server

The web server should serve the `dist/` directory containing the `index.html`
file. The frontend uses paths like `/queue` or `/about` for permalinks; those
paths should also be redirected to the `index.html` file.

The frontend loads an impressum from `/Impressum.html`, as well as CSS and
scripts from the paths `/css` and `/js`, respectively. Those should direct to
the files and directories `dist/Impressum.html`, `dist/css` and `dist/js`
respectively.