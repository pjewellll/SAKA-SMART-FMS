package com.sakasmart;

import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Small, optimized, portable public marketplace images stored in existing TEXT image fields.
 *  A data URI travels with a shared DB (rather than referencing one laptop's file path).
 *  This is for a small-capstone catalog; production scale should use authenticated object storage.
 */
final class MarketPhotoAssets {
    private static final String PREFIX="data:image/jpeg;base64,";
    private static final int MAX_SOURCE_BYTES=8_000_000;
    private static final int MAX_IMAGE_SIDE=6000;
    private static final long MAX_PIXELS=24_000_000;
    private static final int MAX_ENCODED_CHARS=650_000;
    private MarketPhotoAssets(){}

    static boolean isUploaded(String value){return value!=null&&value.startsWith(PREFIX);}
    static String validateReference(String value){
        String s=value==null?"":value.trim();
        if(s.isEmpty())return "";
        if(isUploaded(s)){
            if(s.length()>MAX_ENCODED_CHARS)throw new IllegalArgumentException("Uploaded picture is too large. Choose a smaller photo.");
            return s;
        }
        if(!s.startsWith("https://") || s.length()>1023)throw new IllegalArgumentException("Use an HTTPS picture link or upload an image from your device.");
        return s;
    }
    static String chooseAndEncode(Component parent, int longestSide){
        JFileChooser picker=new JFileChooser();
        picker.setDialogTitle("SAKA-SMART | Choose a Product or Farm Photo");
        picker.setPreferredSize(new Dimension(780,540));
        picker.setApproveButtonText("Use This Photo");
        picker.setApproveButtonToolTipText("Choose the selected image for your storefront");
        picker.setFileSelectionMode(JFileChooser.FILES_ONLY);
        picker.setMultiSelectionEnabled(false);
        picker.setAcceptAllFileFilterUsed(false);
        picker.setFileFilter(new FileNameExtensionFilter("Photos (*.jpg, *.jpeg, *.png, *.webp if supported)","jpg","jpeg","png","webp"));
        // Keep the native file chooser for Windows accessibility and familiar folder navigation.
        if(picker.showOpenDialog(parent)!=JFileChooser.APPROVE_OPTION)return null;
        try {return encode(picker.getSelectedFile().toPath(),longestSide);}
        catch(Exception ex){MarketplaceVisuals.notice(parent,"Photo Upload Error",ex.getMessage(),JOptionPane.ERROR_MESSAGE);return null;}
    }
    static String encode(Path path,int maxSide) throws Exception {
        if(path==null||!Files.isRegularFile(path))throw new IllegalArgumentException("Choose an existing photo.");
        long bytes=Files.size(path);
        if(bytes==0||bytes>MAX_SOURCE_BYTES)throw new IllegalArgumentException("Photo must be smaller than 8 MB.");
        BufferedImage image;
        try(ImageInputStream input=ImageIO.createImageInputStream(path.toFile())){
            if(input==null)throw new IllegalArgumentException("Could not read this photo.");
            Iterator<ImageReader> iter=ImageIO.getImageReaders(input);
            if(!iter.hasNext())throw new IllegalArgumentException("Unsupported image. Use JPG or PNG (or supported WebP).");
            ImageReader reader=iter.next();
            try{
                reader.setInput(input,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>MAX_IMAGE_SIDE||h>MAX_IMAGE_SIDE||(long)w*h>MAX_PIXELS)
                    throw new IllegalArgumentException("Photo dimensions are too large.");
                image=reader.read(0);
            }finally{reader.dispose();}
        }
        if(image==null)throw new IllegalArgumentException("Invalid image file.");
        int side=Math.max(180,Math.min(1100,maxSide));
        for(int n=0;n<4;n++){
            double scale=Math.min(1.0,(double)side/Math.max(image.getWidth(),image.getHeight()));
            int w=Math.max(1,(int)Math.round(image.getWidth()*scale)),h=Math.max(1,(int)Math.round(image.getHeight()*scale));
            BufferedImage jpeg=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=jpeg.createGraphics();
            g.setColor(Color.WHITE);g.fillRect(0,0,w,h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image,0,0,w,h,null);g.dispose();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            Iterator<ImageWriter> writers=ImageIO.getImageWritersByFormatName("jpeg");
            if(!writers.hasNext())throw new IOException("JPEG encoder unavailable.");
            ImageWriter writer=writers.next();
            try(javax.imageio.stream.ImageOutputStream imageOut=ImageIO.createImageOutputStream(out)){
                writer.setOutput(imageOut);ImageWriteParam p=writer.getDefaultWriteParam();
                if(p.canWriteCompressed()){p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);p.setCompressionQuality(.78f);}
                writer.write(null,new IIOImage(jpeg,null,null),p);
            }finally{writer.dispose();}
            String result=PREFIX+Base64.getEncoder().encodeToString(out.toByteArray());
            if(result.length()<=MAX_ENCODED_CHARS)return result;
            side=(int)(side*.7);
        }
        throw new IllegalArgumentException("Photo still too large after compression. Choose another image.");
    }
    static BufferedImage load(String reference){
        if(reference==null||reference.isBlank())return null;
        try{
            byte[] data;
            if(isUploaded(reference)){
                if(reference.length()>MAX_ENCODED_CHARS)return null;
                data=Base64.getDecoder().decode(reference.substring(PREFIX.length()));
            }else{
                if(!reference.startsWith("https://")||reference.length()>1023)return null;
                URL url=URI.create(reference).toURL();
                URLConnection c=url.openConnection();c.setConnectTimeout(2500);c.setReadTimeout(2500);c.setUseCaches(false);
                if(c instanceof HttpURLConnection http)http.setInstanceFollowRedirects(false);
                if(c.getContentLengthLong()>2_000_000)return null;
                try(InputStream in=c.getInputStream()){data=in.readNBytes(2_000_001);}
                if(data.length>2_000_000)return null;
            }
            try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(data))){
                if(input==null)return null;Iterator<ImageReader> readers=ImageIO.getImageReaders(input);if(!readers.hasNext())return null;
                ImageReader r=readers.next();
                try{r.setInput(input,true,true);int w=r.getWidth(0),h=r.getHeight(0);
                    if(w<1||h<1||w>MAX_IMAGE_SIDE||h>MAX_IMAGE_SIDE||(long)w*h>MAX_PIXELS)return null;
                    return r.read(0);
                }finally{r.dispose();}
            }
        }catch(Exception ignored){return null;}
    }
    static ImageIcon icon(String reference,int w,int h){
        BufferedImage src=load(reference);
        if(src==null)return null;
        BufferedImage canvas=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=canvas.createGraphics();g.setColor(new Color(234,248,234));g.fillRect(0,0,w,h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double s=Math.min((double)w/src.getWidth(),(double)h/src.getHeight());
        int sw=Math.max(1,(int)(src.getWidth()*s)),sh=Math.max(1,(int)(src.getHeight()*s));
        g.drawImage(src,(w-sw)/2,(h-sh)/2,sw,sh,null);g.dispose();return new ImageIcon(canvas);
    }
}
