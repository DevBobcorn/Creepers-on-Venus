package com.bobcorn.krcdecoder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class App {
    private static String inPath  = "G:\\Magic\\Creepers on Venus\\Lyrics\\";
    private static String outPath = "G:\\Magic\\Creepers on Venus\\Lyrics Decoded\\";

    private static final char[] miarry = { '@', 'G', 'a', 'w', '^', '2', 't', 'G', 'Q', '6', '1', '-', 'Î', 'Ò', 'n',
            'i' };

    public static void main(String[] args) throws IOException {
        System.out.println("Hello Kugo-u!");

        File dir = new File(inPath); //Create File Object..
        if (dir.exists()) {
            File[] files = dir.listFiles();// Read All Files
            System.out.println(files.length + " Files/Dirs Found.");
            for (int i = 0; i < files.length; i++) {
                String fileName = files[i].getName();
                //System.out.println(fileName.substring(fileName.lastIndexOf(".") + 1) + "<=");
                //System.out.println(fileName.substring(fileName.lastIndexOf(".") + 1).equals("krc"));

                if (files[i].isFile() && (fileName.substring(fileName.lastIndexOf(".") + 1).equals("krc"))) {
                    System.out.println("Decoding File: " + fileName);
                    String result = App.getKrcText(files[i].getAbsolutePath());
                    String newName = fileName.substring(0, fileName.lastIndexOf(".")) + ".txt";
                    App.write(outPath + newName, result);
                }
            }
        } else {
            System.out.println("Directory not found.");
        }

        Scanner in = new Scanner(System.in);
        boolean cont = true;
        while (cont){ // File Processing Loop..
            System.out.println("Input file path:");
            String filePath = in.nextLine();
            
            if (filePath.equals("q")){
                cont = false; // Quit the Program..
                System.out.println("Quit");
                break;
            }

            File curFile = new File(filePath);
            if (curFile.exists()){
                String fileName = curFile.getName();
                if (fileName.substring(fileName.lastIndexOf(".") + 1).equals("krc")){
                    System.out.println("Decoding File: " + filePath);
                    String result = App.getKrcText(filePath);
                    //System.out.println(App.getKrcText(fileName));
                    App.write(outPath + fileName, result);
                }
            }
        }
        
        in.close();
    }

    public static String getKrcText(String filenm) throws IOException {
        File krcfile = new File(filenm);
        byte[] zip_byte = new byte[(int) krcfile.length()];
        FileInputStream fileinstrm = new FileInputStream(krcfile);
        byte[] top = new byte[4];
        fileinstrm.read(top);
        fileinstrm.read(zip_byte);
        int j = zip_byte.length;
        for (int k = 0; k < j; k++) {
            int l = k % 16;
            int tmp67_65 = k;
            byte[] tmp67_64 = zip_byte;
            tmp67_64[tmp67_65] = (byte) (tmp67_64[tmp67_65] ^ miarry[l]);
        }
        String krc_text = new String(ZLibUtils.decompress(zip_byte), "utf-8");
        fileinstrm.close();
        return krc_text;
    }

    public static void write(String path, String content) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(path));

        bw.write(content);
        bw.close();
        System.out.println("Decoded File Generated.");
    }
}