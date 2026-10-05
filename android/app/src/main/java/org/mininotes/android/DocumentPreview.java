package org.mininotes.android;

import java.io.*;
import java.util.zip.*;
import org.xmlpull.v1.XmlPullParser;

/** A bounded text preview of the actual DOCX, never a fabricated page thumbnail. */
final class DocumentPreview {
    static String text(byte[] bytes){
        int remaining=1024*1024,entries=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            ZipEntry entry;byte[] block=new byte[4096];
            while((entry=zip.getNextEntry())!=null&&entries++<100){
                boolean wanted=entry.getName().equals("word/document.xml");ByteArrayOutputStream xml=wanted?new ByteArrayOutputStream():null;int n;
                while((n=zip.read(block))!=-1){remaining-=n;if(remaining<0)return "";if(wanted)xml.write(block,0,n);}
                if(!wanted)continue;
                String source=xml.toString("UTF-8");if(source.contains("<!DOCTYPE")||source.contains("<!ENTITY"))return "";
                XmlPullParser parser=android.util.Xml.newPullParser();parser.setInput(new StringReader(source));StringBuilder text=new StringBuilder();boolean inText=false;
                for(int event=parser.next();event!=XmlPullParser.END_DOCUMENT&&text.length()<600;event=parser.next()){
                    String tag=parser.getName();
                    if(event==XmlPullParser.START_TAG){inText="t".equals(tag)||"w:t".equals(tag);if("br".equals(tag)||"w:br".equals(tag))text.append('\n');else if("tab".equals(tag)||"w:tab".equals(tag))text.append(' ' );}
                    else if(event==XmlPullParser.TEXT&&inText)text.append(parser.getText());
                    else if(event==XmlPullParser.END_TAG){inText=false;if("p".equals(tag)||"w:p".equals(tag))text.append('\n');}
                }
                return text.toString().trim();
            }
        }catch(Exception unavailable){/* A preview never blocks opening the original in the editor. */}
        return "";
    }
}
