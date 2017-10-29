# Dockered countmatic server
About running a countmatic server in a docker container.
## Build image
Use buldfile to docker-build the container image, i.e.

sudo docker build -t countmatic/springserver:1.0.0 -t countmatic/springserver:1 -t countmatic/springserver:latest .

## Run in container
Run in foreground with log to stdout:

sudo docker run -p 8080:8080 -link redis:redis -it  countmatic/springserver

Or run as daemon like background task:

sudo docker run -p 8080:8080 --link redis:redis -d  countmatic/springserver

redis is excpected to be the container running the redis instance, like

sudo docker run --name redis  -p 6379:6379 -v /home/rainer/web/redis:/data -h redis -d redis redis-server --appendonly yes


