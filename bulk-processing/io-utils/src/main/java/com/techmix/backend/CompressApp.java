package com.techmix.backend;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class CompressApp {

    public static void main(String... args) throws IOException {

        FileInputStream in = new FileInputStream("full_export.json.bz2");

//        FileOutputStream out = new FileOutputStream("full_export.json");
//
//        BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(in);
//
//        final byte[] buffer = new byte[1024 * 1024 * 5];
//        int n;
//        while (-1 != (n = bzIn.read(buffer))) {
//            out.write(buffer, 0, n);
//            System.err.println("Written 1024 * 1024 * 50 bytes");
//        }
//        out.close();
//        bzIn.close();

        FileOutputStream out = new FileOutputStream("full_export_1MB.json");

        BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(in);

        byte[] buffer = new byte[1024 * 1024 * 1];
        int n = bzIn.read(buffer);
        out.write(buffer, 0, n);
        out.close();
        bzIn.close();

        FileInputStream in5MB = new FileInputStream("full_export.json.bz2");
        FileOutputStream out5MB = new FileOutputStream("full_export_5MB.json");

        BZip2CompressorInputStream bzIn5MB = new BZip2CompressorInputStream(in5MB);

        buffer = new byte[1024 * 1024 * 5];
        n = bzIn5MB.read(buffer);
        out5MB.write(buffer, 0, n);
        out5MB.close();
        bzIn5MB.close();

        FileInputStream in25MB = new FileInputStream("full_export.json.bz2");
        FileOutputStream out25MB = new FileOutputStream("full_export_25MB.json");

        BZip2CompressorInputStream bzIn25MB = new BZip2CompressorInputStream(in25MB);

        buffer = new byte[1024 * 1024 * 25];
        n = bzIn25MB.read(buffer);
        out25MB.write(buffer, 0, n);
        out25MB.close();
        bzIn25MB.close();
    }

}
