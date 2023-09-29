package org.example;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import static java.lang.StringTemplate.STR;

public class Main {
    public static List<File> downloadReleaseAssets(GHRelease release) throws IOException {

        List<File> downloadedFiles = new ArrayList<>();
        File tempDir = createTempDirectory();



        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            for (GHAsset asset : release.listAssets()) {
                String assetName = asset.getName();
                String downloadUrl = asset.getBrowserDownloadUrl();


                HttpGet request = new HttpGet(downloadUrl);

                httpClient.execute(request, httpResponse -> {
                    // Get the content length of the response
                    long contentLength = httpResponse.getEntity().getContentLength();
                    System.out.println(STR."Downloading \{assetName} \{contentLength/1048576}MB " );

                    // Create a file in the temporary directory
                    File tempFile = new File(tempDir, assetName);

                    // Create an input stream to read the response from the HTTP request
                    try (InputStream is = httpResponse.getEntity().getContent()) {
                        // Create an output stream to write the response to a file
                        try (OutputStream os = new FileOutputStream(tempFile)) {
                            // Create a buffer to hold the data being transferred
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            long totalBytesRead = 0;

                            // Read from the input stream and write to the output stream
                            while ((bytesRead = is.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                                totalBytesRead += bytesRead;

                                // Calculate the percentage of the file that has been downloaded
                                int percentComplete = (int) ((totalBytesRead * 100) / contentLength);

                                // Update the progress bar or display the percentage downloaded
//                                if(percentComplete % 10 == 0) {
//                                    // System.out.println(percentComplete + "% downloaded");
//                                    System.out.println(STR."\{assetName} \{percentComplete}% downloaded");
//
//                                }
                            }
                        }
                    }
                    downloadedFiles.add(tempFile);
                    System.out.println(STR."Downloaded \{tempFile.getAbsolutePath()}");

                    return null;
                });

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return downloadedFiles;
    }

    private static File createTempDirectory() throws IOException {
        File tempDir = File.createTempFile("gh-release", "");
        if (!tempDir.delete() || !tempDir.mkdir()) {
            throw new IOException("Could not create temp directory: " + tempDir.getAbsolutePath());
        }
        return tempDir;
    }

    public static void main(String[] args) {
        System.out.println("Checking ADSB.lol Github releases");

        try {
            // Initialize GitHub object with your personal access token
            GitHub github =
                    new GitHubBuilder()
                            .withOAuthToken(
                                    "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")
                            .build();

            // Get a specific repository (replace "owner" and "repository-name" with the actual
            // values)
            GHRepository repo = github.getRepository("adsblol/globe_history");

            // Get the list of releases
            List<GHRelease> releases = repo.listReleases().asList();
            System.out.println("Number of releases: " + releases.size());
            GHRelease firstRelease = releases.getLast();
//            GHRelease firstRelease = repo.getLatestRelease();

            List<File> downloadedFiles = downloadReleaseAssets(firstRelease);
            System.out.println("Downloaded " + downloadedFiles.size() + " files");

            // Print out details of each release
//            for (GHRelease release : releases) {
//                System.out.println("ID: " + release.getId());
//                System.out.println("Tag: " + release.getTagName());
//                System.out.println("Name: " + release.getName());
//                System.out.println("Published at: " + release.getPublished_at());
//                System.out.println("----------------------");
//            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
