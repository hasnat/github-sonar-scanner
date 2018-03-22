package com.listener;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import com.jcabi.log.VerboseProcess;

import fi.iki.elonen.NanoHTTPD;


public class App extends NanoHTTPD {

    static String REPOS_PATH = Optional.ofNullable(System.getenv("REPOS_PATH")).orElse("/tmp/temp_git_repos/");
    static String HTTP_PORT = Optional.ofNullable(System.getenv("HTTP_PORT")).orElse("");
    static String GITHUB_TOKEN = Optional.ofNullable(System.getenv("GITHUB_TOKEN")).orElse("");
    static String GITLAB_URL = Optional.ofNullable(System.getenv("GITLAB_URL")).orElse("");
    static String GITLAB_TOKEN = Optional.ofNullable(System.getenv("GITLAB_TOKEN")).orElse("");
    static String SONAR_URL = Optional.ofNullable(System.getenv("SONAR_URL")).orElse("");
    static String SONAR_TOKEN = Optional.ofNullable(System.getenv("SONAR_TOKEN")).orElse("");

    public App() throws IOException, NumberFormatException {
        super(Integer.parseInt(HTTP_PORT));
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println(String.format("\nRunning! Point your browsers to http://0.0.0.0:%s/ \n", HTTP_PORT));
    }

    public static void main(String[] args) {
        try {
            new App();
        } catch (IOException ioe) {
            System.err.println("Couldn't start server:\n" + ioe);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {

        Method method = session.getMethod();
        if (Method.PUT.equals(method) || Method.POST.equals(method)) {
            try {
                final HashMap<String, String> map = new HashMap<String, String>();
                session.parseBody(map);
                final String json = map.get("postData");
                System.out.println("\nRequest \n uri: " + session.getUri() + "\nbody: " + json);
                Runnable r = () -> runSonarAnalysis(json);
                new Thread(r).start();
            } catch (IOException | ResponseException e) {
                System.err.println("Request Error:\n" + e);
                return newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "Error!");
            }
            return newFixedLengthResponse("OK"); // Or postParameter.
        }
        return newFixedLengthResponse("I ok, I wait.");
    }

    private void runSonarAnalysis(String postData) {

        String gitRepoName = "";
        String headCommit = "";
        String gitRepoUrl = "";
        String gitBranchName = "";
        int gitProjectId = 0;
        int pullRequest = 0;
        boolean isGithub = false;
        boolean isGitlab = false;
        try {
            // github
            DocumentContext jsonContext = JsonPath.parse(postData);

            headCommit = jsonContext.read("$['pull_request']['head']['sha']");
            gitRepoName = jsonContext.read("$['pull_request']['head']['repo']['full_name']");
            gitBranchName = jsonContext.read("$['pull_request']['head']['ref']");
            pullRequest = jsonContext.read("$['pull_request']['number']");
            gitRepoUrl = jsonContext.read("$['pull_request']['head']['repo']['clone_url']");
            gitRepoUrl = gitRepoUrl.replace("://", "://" + GITHUB_TOKEN + "@");
            isGithub = true;
        } catch (PathNotFoundException e) {
            System.out.println("Path not found ( not a github pull request )");
        }

        if (!isGithub) {
            try {
                // gitlab
                DocumentContext jsonContext = JsonPath.parse(postData);

                gitBranchName = jsonContext.read("$['object_attributes']['source_branch']");
                headCommit = jsonContext.read("$['object_attributes']['last_commit']['id']");
                gitRepoName = jsonContext.read("$['object_attributes']['source']['path_with_namespace']");
                gitProjectId = jsonContext.read("$['project']['id']");
                gitRepoUrl = jsonContext.read("$['object_attributes']['source']['git_http_url']");
                gitRepoUrl = gitRepoUrl.replace("://", "://gitlab-ci-token:" + GITLAB_TOKEN + "@");
                isGitlab = true;
            } catch (PathNotFoundException e) {
                System.out.println("Path not found ( not a gitlab pull request )");
            }
        }
        if (!isGithub && !isGitlab) {
            System.out.println("Skip sonar scan trigger");
            return;
        }
        String localPath = REPOS_PATH + gitRepoName + "/" + headCommit;

        try {
            runCommand("mkdir -p " + localPath);
            runCommand("git clone --depth 1 -b " + gitBranchName + " --single-branch " + gitRepoUrl + " .", localPath);
            String scannerCommand = "/usr/src/sonar-scanner/sonar-scanner -Dproject.home=.";

            if (isGithub) {
                scannerCommand = scannerCommand +
                        " -Dsonar.github.pullRequest=" + pullRequest +
                        " -Dsonar.github.repository=" + gitRepoName +
                        " -Dsonar.github.oauth=" + GITHUB_TOKEN;
            } else {
                scannerCommand = scannerCommand +
                        " -Dsonar.gitlab.failure_notification_mode=commit-status" +
                        " -Dsonar.gitlab.json_mode=CODECLIMATE" +
                        " -Dsonar.gitlab.commit_sha=" + headCommit +
                        " -Dsonar.gitlab.project_id=" + gitProjectId +
                        " -Dsonar.gitlab.ref_name=" + gitBranchName +
                        (GITLAB_TOKEN.equals("") ? "" : " -Dsonar.gitlab.user_token=" + GITLAB_TOKEN) +
                        (GITLAB_URL.equals("") ? "" : " -Dsonar.gitlab.url=" + GITLAB_URL) +
                        " -Dsonar.host.url=" + SONAR_URL +
                        " -Dsonar.verbose=true" +
                        " -Dsonar.issuesReport.console.enable=true" +
                        " -Dsonar.login=" + SONAR_TOKEN;
            }
            runCommand(scannerCommand, localPath);
            if (!localPath.equals("/")) {
                runCommand("rm -rf " + localPath);
            }
            System.out.println("Done, bring me another!");
        } catch (IOException | InterruptedException e) {
            System.out.println("Exception happened - here's what I know: " + e);
            e.printStackTrace();
        }
    }

    private void runCommand(String command) throws IOException, InterruptedException {
        runCommand(command, "");
    }

    private void runCommand(String command, String workingDirectory) throws IOException, InterruptedException {
        System.out.println("\n$ " + command + "\n");
        Process p;
        if (workingDirectory.equals("")) {
            p = Runtime.getRuntime().exec(command);
        } else {
            p = Runtime.getRuntime().exec(command, null, new File(workingDirectory));
        }
        VerboseProcess.Result vpr = new VerboseProcess(p).waitFor();
        System.out.println(vpr.stdout() + "\n" + vpr.stderr() + "\n--\n");
    }
}