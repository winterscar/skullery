# skullery

Skullery is a Recipe app that has a *database* of ingredients rather than free text.

This enables many cool features:

- Proper shopping list generation (with categorisation, aggregation, etc…)
- Consistent scaling and adjustment options
- Unit swapping on-the-fly
- Ingredient alternatives suggestions

Other useful features:

- Measure time of each step to calculate recipe total time
- Sub-recipes
- What can I make with these ingredients?

## Getting Started

1. Start the application: `lein run`
2. Go to [localhost:8888/ide](http://localhost:8888/ide) to see the graphql ide
4. Run tests with `lein test`. Read the tests at test/skullery/*_test.clj.

## Configuration

To configure logging see config/logback.xml. By default, the app logs to stdout and logs/.
To learn more about configuring Logback, read its [documentation](http://logback.qos.ch/documentation.html).


## Developing your service

1. Jack in using calva, and select the skullery profile.

### [Docker](https://www.docker.com/) container support

1. Configure your service to accept incoming connections (edit service.clj and add  ::http/host "0.0.0.0" )
2. Build an uberjar of your service: `lein uberjar`
3. Build a Docker image: `sudo docker build -t skullery .`
4. Run your Docker image: `docker run -p 8080:8080 skullery`