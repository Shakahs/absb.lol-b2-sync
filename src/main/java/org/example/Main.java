package org.example;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import com.backblaze.b2.client.contentSources.B2ContentSource;
import com.backblaze.b2.client.contentSources.B2FileContentSource;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.B2FileVersion;
import com.backblaze.b2.client.structures.B2ListFileNamesRequest;
import com.backblaze.b2.client.structures.B2ListUnfinishedLargeFilesRequest;
import com.backblaze.b2.client.structures.B2UploadFileRequest;
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
    public static List<File> downloadReleaseAssets(GHRelease release,List<String> preloadedFileNames) throws IOException {

        List<File> downloadedFiles = new ArrayList<>();
        File tempDir = createTempDirectory();



        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            for (GHAsset asset : release.listAssets()) {
                String assetName = asset.getName();
                String downloadUrl = asset.getBrowserDownloadUrl();

                if (preloadedFileNames.contains(assetName)) {
                    System.out.println(STR."Skipping \{assetName} as it already exists");
                    continue;
                }


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
            httpClient.close();

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

    public static void uploadFiles(List<File> filesToUpload, B2StorageClient b2client,List<String> preloadedFileNames  ) {
        try   {

            for (File file : filesToUpload) {
                if (preloadedFileNames.contains(file.getName())) {
                    System.out.println(STR."Skipping \{file.getName()} as it already exists");
                    continue;
                }
                String fileName = file.getName();
                System.out.println(STR."Uploading \{fileName}");

                String contentType = "application/x-tar";
               try {
                   contentType= Files.probeContentType(Path.of(file.getAbsolutePath()));
               } catch (IOException e) {
                   e.printStackTrace();}

                B2UploadFileRequest request = B2UploadFileRequest
                        .builder("57f71749bef6b9b38b990c12", fileName, contentType ,   B2FileContentSource.builder(file).build())
                        .build();

                ExecutorService executorService = Executors.newFixedThreadPool(2);
                b2client.uploadLargeFile(request , executorService );
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.out.println("executorService timed out during upload");
                        executorService.shutdownNow(); // Force shutdown
                    }
                } catch (InterruptedException e) {
                    System.out.println("executorService was interrupted");
                    executorService.shutdownNow(); // Preserve the interrupt status
                    Thread.currentThread().interrupt();
                }

                System.out.println(STR."Uploaded \{fileName}");
            }

        } catch (B2Exception e) {
            // Exception handling here
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("Checking ADSB.lol Github releases");
        B2StorageClient b2client = B2StorageClientFactory
                .createDefaultFactory()
                .create("0057779e693b9c20000000001", "K0056wTbaRoWE4Ki2m2USgicRBeK9Ps", "AdsbLolMirror");

        try {
            B2ListUnfinishedLargeFilesRequest request = B2ListUnfinishedLargeFilesRequest.builder("57f71749bef6b9b38b990c12").build();

            // Fetch and iterate through the unfinished large files.
            for (B2FileVersion file : b2client.unfinishedLargeFiles(request)) {
                // Delete each unfinished large file.
                b2client.cancelLargeFile(file.getFileId());
                System.out.println("Deleted unfinished large file with ID: " + file.getFileId());
            }
        } catch (B2Exception e) {
            System.out.println("Error deleting unfinished large files");
            e.printStackTrace();
        }

        // Preload filenames
        List<String> preloadedFileNames = new ArrayList<>();
        B2ListFileNamesRequest     b2ListRequest =    B2ListFileNamesRequest.builder("57f71749bef6b9b38b990c12").setMaxFileCount(10000).build();
        try {
            Iterable<B2FileVersion> fileVersions = b2client.fileNames(b2ListRequest);
            for (B2FileVersion fileVersion : fileVersions) {
                preloadedFileNames.add(fileVersion.getFileName());
                System.out.println("Preloaded filename: " + fileVersion.getFileName());
            }
        } catch (B2Exception e) {
            System.out.println("Error preloading filenames");
            e.printStackTrace();
        }

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
            List<GHRelease> releases = repo.listReleases().asList().reversed();
            System.out.println("Number of releases: " + releases.size());
//            GHRelease firstRelease = releases.getLast();
//            GHRelease firstRelease = repo.getLatestRelease();
            List<GHRelease> newArray = new ArrayList<GHRelease>( );
            newArray.add(releases.getFirst());

            for (GHRelease release : releases) {
                List<File> downloadedFiles = downloadReleaseAssets(release,preloadedFileNames);
                uploadFiles(downloadedFiles, b2client, preloadedFileNames);
            }
            System.out.println("Done");
            b2client.close();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
