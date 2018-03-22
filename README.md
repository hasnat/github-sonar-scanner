# trigger-sonar-scanner
Github/Gitlab webhook listener to fire sonar-scanner

### Running the api
```
docker run -it -d \
    --name trigger-sonar-scanner \
    -p 8000:8080 \
    -e HTTP_PORT=8080 \
    -e SONAR_TOKEN=XXXXX \
    -e SONAR_URL=https://sonar.website \
    -e GITHUB_TOKEN=YYYYY \
    -e GITLAB_TOKEN=YYYY \
    -e GITLAB_URL=https://gitlab.website \
    hasnat/trigger-sonar-scanner

```

Setup webhooks for github or gitlab

Features
--------
- pull requests code review for github
- requests code review for gitlab


Available environment variables
-------------------------------
- `GITHUB_TOKEN`    with [repo, write:discussion] permissions ( https://github.com/settings/tokens )
- `GITLAB_TOKEN`    with api, read permissions - required for cloning repo ( token can be set in Sonar admin gitlab plugin setting but still required here )
- `GITLAB_URL`      your gitlab url, ( can be set in Sonar admin gitlab plugin setting )
- `SONAR_URL`       sonar URL - required
- `SONAR_TOKEN`     sonar login token - required
- `REPOS_PATH`      path where App will temporarily clone repo - default `/tmp/temp_git_repos/`
- `HTTP_PORT`       port for api to run - default 8080

If no gitlab or github tokens provided or set in sonar

### Development

Clone this repo and
#### Run local Sonar
```
docker run -d --name sonarqube -p 9000:9000 -p 9092:9092 sonarqube
```

#### Run local Sonar Trigger API
```
docker build -t trigger-sonar-scanner-api .

docker run -it --rm \
    --name trigger-sonar-scanner \
    -p 8000:8080 \
    -e HTTP_PORT=8080 \
    -e SONAR_TOKEN=XXXXX \
    -e GITLAB_URL=https://gitlab.website \
    -e SONAR_URL=http://docker.for.mac.localhost:9000 \
    -e GITLAB_TOKEN=YYYY \
    -e GITHUB_TOKEN=YYYYY \
    trigger-sonar-scanner-api

ngrok http 8000
```

## References
- https://sonarsource.bintray.com/Distribution/sonar-scanner-cli/
- https://developer.github.com/v3/activity/events/types/#pullrequestevent
- https://docs.sonarqube.org/display/PLUG/GitHub+Plugin
- https://github.com/gabrie-allaigre/sonar-gitlab-plugin
- https://ngrok.com/
