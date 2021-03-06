# VelCom

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [VelCom](#velcom)
    - [Installation guide](#installation-guide)
        - [The easy way™](#the-easy-way)
        - [The docker way™](#the-docker-way)
        - [The hard way™](#the-hard-way)
    - [Starting VelCom](#starting-velcom)
    - [Configuring the web server](#configuring-the-web-server)
        - [Cloning Repositories via SSH](#cloning-repositories-via-ssh)
    - [Using the REST-API manually](#using-the-rest-api-manually)
    - [Monitoring VelCom](#monitoring-velcom)
        - [Public Monitoring instance](#public-monitoring-instance)

<!-- markdown-toc end -->

## Installation guide

Ensure that the following tools are installed on your system:
- `jdk` (Version 11 or newer)
- `make`
- `maven` (Maven 3)
- `yarn`

### The easy way™

Run the `guided_install.py` script and follow its instructions.

### The docker way™

Go to the root directory of this project and build the docker image using the
makefile.

For an explanation of `USER_ID`, have a look at step 5 below. If you do not provide
a UID, the Makefile uses 1003.
```
$ make docker-build-server USER_ID=<uid>
```

This will build the backend and the frontend, copy all relevant files into a
temporary folder called `.docker` and execute `sudo docker build` in it.
The tag of the image is `velcom-server:latest`.

Now you need to actually configure and launch the server.

1. Create a config directory to mount in the docker image
2. Create a data directory to mount in the docker image
3. Set up a configuration file by copying the `example_config.yml` to the
   created config directory
4. Adjust the `jdbcUrl` field to point to wherever you mounted the data
   directory when running the container.
5. Optionally create a SSH folder containing a private key the backend should
   use for cloning repositories.
   If you do this *make sure the user ids match! Otherwise SSH will not get the
   permissions it expects!*. The Dockerfiles provide the build argument
   `USER_ID` for this. You can use it with `docker build -t velcom:latest
   --build-arg USER_ID=<uid>`. The default Makefiles use a user id of 1003,
   adjust this to your needs.
6. Start the container

```sh
$ mkdir /install/dir/configs
$ mkdir /install/dir/data
$ cp backend/backend/src/main/resources/example_config.yml /install/dir/configs
$ vi /install/dir/configs # Adjust JDBC url to point to whereever you mounted
                          # the config dir in the docker image
                          # This example uses
                          # "jdbc:sqlite:file:/home/velcom/data/data.db"
$ mkdir /install/dir/ssh  # Add a private ssh key the backend should use here
$ docker run                                                  \
        # Delete container on exit if you want
        --rm                                                  \
        # Mount the directory containing the server config
        -v /install/dir/configs:/home/velcom/config           \
        # Mount the data directory to persist it across container restarts
        -v /install/dir/data:/home/velcom/data                \
        # Expose the frontend port (80) to port 9080 (decide for yourself)
        -p 127.0.0.1:9080:80                                  \
        # Expose the backend api port (81) to port 9081 (decide for yourself)
        # if you use the single port image you can omit this mapping
        -p 127.0.0.1:9081:81                                  \
        # Expose the runner port (82) to port 9082 (decide for yourself)
        -p 127.0.0.1:9666:82                                  \
        # Give it read access to an SSH key you added to the relevant repositories
        # If you do this make sure the UIDs match! You can set the UID with the
        # Makefile or directly as a docker build argument
        -v /install/dir/ssh:/root/.ssh:ro                     \
        # Start the image
        velcom-server                                         \
        # Pass it the path to the server config file
        /home/velcom/config/example_config.yml
```

### The hard way™

Configure the url and port the frontend uses for calling the backend's API. This
command assumes you are in the base directory of the `velcom` repo.

```
$ echo 'VUE_APP_BASE_URL="https://domain.com:8080"' > frontend/.env.production.local
```

Clone the repo and use `make` to build the backend and frontend.

```
$ git clone velcom-repo-url
$ cd velcom
$ make
```

Copy the `backend.jar` and `example_config.yml` files to the location where the
backend should run. These commands assume you are in the base directory of the
`velcom` repo. Make sure the `server.applicationConnectors.port` in the config
match what you told the frontend with the `VUE_APP_BASE_URL`.

```
$ cp backend/backend/target/backend.jar install/dir/
$ cp backend/backend/src/main/resources/example_config.yml install/dir/config.yml
```

Choose the amount of memory and milliseconds that you want the hashing algorithm
to take, and then edit the backend config file. These commands assume you're in
the backend install dir.

```
$ java -jar backend.jar hashPerformance <milliseconds> <memory in kib>
$ vi install/dir/config.yml
```

Copy the `runner.jar` and `example_config.json` files to the location where the
runner should run and modify the config file. These commands assume you are in
the base directory of the `velcom` repo.

Each runner must have a unique name! If not, the server will disconnect the old
runner as soon as a new runner with the same name connects.

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

```
$ cp -r frontend/dist/ path/to/install/dir/
$ vi path/to/install/dir/dist/Impressum.html
```

### Running backend or frontend on a single port

It is possible to run the backend *and* the frontend on a single port.

1. To do this you need to make the backend accessible on some sub path (like
   `/api/<path>`) and tell your reverse proxy to forward requests to this path
   to the backend. Make sure you remove the prefix (e.g. `/api/`) before
   forwarding the requests. If you do not do that, be prepared to just receive
   404s when accessing the backend.
2. Set the backend url in the frontend's `.env` file and *include the backend
   prefix at the end*!
   An example: `https://example.com:8080/api/`.
3. Rebuild the frontend (`yarn build` in the frontend directory)
4. Deploy the new frontend `dist` folder

You can refer to [docs/nginx-site-single-port](docs/nginx-site-single-port) for
a working reference config.

## Starting VelCom

In the directory where the backend was installed, run the `backend.jar`. The
first argument should be `server` and the second argument the path to the config
file. If you followed the installation steps above, this looks like:

```
$ java -jar backend.jar server config.yml
```
Make sure the working directory is writable for the user you excute the backend
with, as it will store archive data in "$PWD/data/archives".

In the directory where the runner was installed, run the `runner.jar`. The first
(and only) argument should be the path to the config file. If you've followed
the installation steps above, this looks like:

If your benchmark repo is cloned over SSH and you do not have a working SSH
setup for that remote server, starting the backend will fail with an error
message. See [Cloning repositories via SSH](#cloning-repositories-via-ssh) for
further information.

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

An example config for nginx that implements these rules can be found
[in the `docs` directory](docs/nginx-site).

### Cloning repositories via SSH

To combat the less than optimal speed of JGit VelCom will prefer using the
native git installation for cloning repositories. The native installation and
VelCom's JGit handle SSH connections differently:

* **JGit:** No host-key verification is performed *at all*
* **Native git:** Host key verification is done normally. Please note that *the
  first connection to a new host will fail*, as VelCom can not ask you to verify
  the key! As soon as you connected *once* normally (via ssh or the command line
  git) and the host is in `known_hosts`, cloning will work normally. If the host
  key changes, VelCom will refuse cloning or fetching any repo from that host.

## Using the REST-API manually

The backend provides a REST-API for communication with the frontend. This setup
makes it easy to write small scripts to interact with the backend using tools
like `curl`. For a complete overview of the available endpoints, see the API's
[openapi documentation](docs/rest_api_openapi.yaml). It was created using
[Stoplight Studio](https://stoplight.io/studio/), so if you want to import and
view the API there, use the
[stoplight studio version](docs/rest_api_stoplight_studio.yaml).

The API uses JSON bodies and HTTP Basic authentication. To authenticate as web
admin, use username `admin` and the web admin token as password. To authenticate
as repo admin, use the repo id as username and the repo token as password. The
web admin and repo tokens must have a length of at least 1.


## Monitoring VelCom

It is always nice to get notified if VelCom just suddenly died a gruesome death
or is not working correctly. To help you with this task, VelCom exposes two
metric endpoints on its *admin connector* (look in the config for the
`adminConnectors`
to find out what port it is running on):
* `/metrics` -- Aggregated server health metrics from Dropwizard in JSON Format
* `/prometheusMetrics` -- Aggregated server health and status metrics in a
  [Prometheus](https://prometheus.io/) compatible format.

The Prometheus endpoint can easily be integrated in an existing
Prometheus/Grafana based workflow. There is a small Aaaaaaah project called
[Pseumonitor](https://git.scc.kit.edu/aaaaaaah/pseumonitor) which provides a
`docker-compose.yml` file that automatically sets up
[node_exporter](https://github.com/prometheus/node_exporter),
[Grafana](https://grafana.com/) and [Prometheus](https://prometheus.io/) to
scrape a locally running VelCom instance. This can be adapted to your
needs quite easily and might serve as a nice starting point.

### Public Monitoring instance

There is a public instance of Pseumonitor running at
[https://velcom.aaaaaaah.de:667](https://velcom.aaaaaaah.de:667/d/rKedptwWk/velcom-dashboard?orgId=1&refresh=30s).
The username is `admin` and the password is `Aaaaaaah`. The Grafana instance
currently features a single dashboard displaying server health metrics (CPU
usage, disk space) and application information (Heap usage, requests, response
times, 5xxer responses, etc.).
