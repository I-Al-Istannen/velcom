# Base image is java 11
FROM openjdk:11-slim

# We use nginx as a reverse proxy
RUN apt update && apt install nginx -y

# Preconfigured nginx configs for development on aaaaaaah.de
COPY nginx-site /etc/nginx/sites-available
COPY nginx.conf /etc/nginx/nginx.conf

RUN ["ln", "-s", "/etc/nginx/sites-available/nginx-site", "/etc/nginx/sites-enabled"]
RUN ["rm", "/etc/nginx/sites-enabled/default"]

COPY start-backend-docker.sh /home/velcom/start.sh

# Include backend files
COPY backend.jar /home/velcom/velcom.jar
COPY dist /home/velcom/frontend

# Expose the config dir
VOLUME ["/home/velcom/config"]

# Frontend serving port (from nginx config)
EXPOSE 80
# Backend API port (from nginx config)
EXPOSE 81
# Runner port (from nginx config)
EXPOSE 82

# Execute the server, needs the path to the configuration file passed as an argument
ENTRYPOINT ["/home/velcom/start.sh"]
