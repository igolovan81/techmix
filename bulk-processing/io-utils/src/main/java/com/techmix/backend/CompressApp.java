package com.techmix.backend;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class CompressApp {

    public static void main(String... args) throws IOException {

        FileInputStream in = new FileInputStream("archive.bz2");
        FileOutputStream out = new FileOutputStream("archive.tar");
        BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(in);
        final byte[] buffer = new byte[1024];
        int n = 0;
        while (-1 != (n = bzIn.read(buffer))) {
            out.write(buffer, 0, n);
        }
        out.close();
        bzIn.close();
    }

}
