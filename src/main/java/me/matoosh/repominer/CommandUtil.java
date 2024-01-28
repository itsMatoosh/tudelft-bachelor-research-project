package me.matoosh.repominer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class CommandUtil {
    public static String executeCommand(String command, File dir) {
        String javaHome8 = "/Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home";
        String javaHome11 = "/Library/Java/JavaVirtualMachines/openjdk-11.jdk/Contents/Home";
        String javaHome17 = "/Users/itsmatoosh/Library/Java/JavaVirtualMachines/openjdk-17.0.2/Contents/Home";
        String javaHome = javaHome8;
        try {
            log(command);
            Process process = Runtime.getRuntime().exec(command, new String[]{"JAVA_HOME=" + javaHome, "ANDROID_HOME=/Users/itsmatoosh/Library/Android/sdk"}, dir);
            StringBuilder stringBuilder = new StringBuilder();
            saveOutput(process.getInputStream(), stringBuilder);
            logErrors(process.getErrorStream());
            process.waitFor();
            return stringBuilder.toString();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void logErrors(InputStream inputStream) {
        new Thread(() -> {
            Scanner scanner = new Scanner(inputStream, "UTF-8");
            while (scanner.hasNextLine()) {
                log(scanner.nextLine());
            }
            scanner.close();
        }).start();
    }

    private static void saveOutput(InputStream inputStream, StringBuilder builder) {
        new Thread(() -> {
            Scanner scanner = new Scanner(inputStream, "UTF-8");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                log(line);
                builder.append(line).append("\n");
            }
            scanner.close();
        }).start();
    }

    private static synchronized void log(String message) {
        System.out.println(message);
    }
}
