package org.bxroberts;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.OutputStream;
import java.util.regex.*;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.contentstream.operator.Operator;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;


import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.*;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.*;

/**
 * Hello world!
 *
 */
/*
public class App
{
    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
    }
}
*/


/**
 * This is an example on how to remove text from PDF document.
 *
 * Note:
 * ------------
 * Because of nature of the PDF structure itself, actually this will not work 100% able to find text that need to be replaced.
 * There are other solutions for that, for example using PDFTextStripper.
 *
 * @author Christian H <chadilukito@gmail.com>
 *
 * Source: https://github.com/chadilukito/Apache-PdfBox-2-Examples/blob/master/ReplaceText.java
 */
public final class App
{
    /**
     * Default constructor.
     */
    private App()
    {
        //example class should not be instantiated
    }

    /**
     * This will remove all text from a PDF document.
     *
     * @param args The command line arguments.
     *
     * @throws IOException If there is an error parsing the document.
     */
    public static void main( String[] args ) throws IOException
    {
	    if( args.length != 3 )
	    {
		    usage();
		    System.exit(1);
	    }

	    String searchString = args[0];
	    String replacement = "****";

            String infile = args[1];
            String outfile = args[2];

            // for (int i = 0; i < searchString.length(); i++) {
            //         replacement += "*";
            // }

	    PDDocument document = null;
	    try
	    {
		    document = PDDocument.load( new File(infile) );
		    if( document.isEncrypted() )
		    {
			    System.err.println( "Error: Encrypted documents are not supported for this example." );
			    System.exit(1);
		    }

		    System.out.println(searchString + " => "+ replacement);

		    document = _ReplaceText(document, searchString, replacement);
		    document.save(outfile);
	    }
	    finally
	    {
		    if( document != null )
		    {
			    document.close();
		    }
	    }
    }

    private static void drawRect(PDPageContentStream content, Rectangle rect, Color color, boolean fill) throws IOException {
            //Setting the non stroking color
            content.setNonStrokingColor(Color.DARK_GRAY);
            //Drawing a rectangle 
            // Rectangle rect = new Rectangle(startX, startY, width, height);
            // Rectangle rect = new Rectangle(200, 650, 100, 100);
            // content.addRect(200, 650, 100, 100);
            content.addRect(rect.x, rect.y, rect.width, rect.height);
            //Drawing a rectangle
            content.fill();
    }

    private static PDDocument _ReplaceText(PDDocument document, String searchString, String replacement) throws IOException
    {
        if (StringUtils.isEmpty(searchString) || StringUtils.isEmpty(replacement)) {
            return document;
        }

        int pageNumber = -1;
        for ( PDPage page : document.getPages() )
        {
                pageNumber++;

                final List<Rectangle> redactionRects = new ArrayList<Rectangle>();
                PDFTextStripper stripper = new PDFTextStripper()
                {
                        @Override
                        protected void startPage(PDPage page) throws IOException
                        {
                                startOfLine = true;
                                super.startPage(page);
                        }
                        @Override
                        protected void writeLineSeparator() throws IOException
                        {
                                startOfLine = true;
                                super.writeLineSeparator();
                        }

                        @Override
                        protected void writeString(String text, List<TextPosition> textPositions) throws IOException
                        {
                                String textString = "";
                                for(TextPosition t: textPositions) {
                                        textString += t.toString();
                                }
                                // System.out.println("textString: " + textString);

                                String[] regexes = {
                                        // email
                                        "[a-zA-Z0-9.]+@[a-zA-Z0-9.]+",
                                        // phone
                                        "\\d+\\s\\d+\\s\\d+\\s\\d*\\s*\\d*\\s*"
                                };

                                // find matches in this line, this is just in the text
                                // we're going to add them to matches, then later use the
                                // match strings to find the token streams and get the
                                // bounding box
                                List<String> matches = new ArrayList<String>();
                                for(String re: regexes) {
                                        Matcher m = Pattern.compile(re).matcher(textString);
                                        while(m.find()) {
                                                String match = m.group(0);
                                                System.out.println("MATCH: " + match);
                                                matches.add(match);
                                        }
                                }

                                for(String match: matches) {
                                        int startIndex = 0;
                                        int matchStartIndex  = -1;
                                        int matchEndIndex = -1;
                                        while ((matchStartIndex = textString.indexOf(match, startIndex)) > -1) {
                                                // TODO: get index of match start and end
                                                matchEndIndex = matchStartIndex + match.length();
                                                System.out.println("matchEndIndex: " + matchEndIndex + " matchStartIndex: " + matchStartIndex);
                                                // TODO: get token of match start
                                                TextPosition startToken = textPositions.get(matchStartIndex);
                                                // TODO: get last token
                                                TextPosition endToken = textPositions.get(matchEndIndex - 1);
                                                // TODO: compute bounding box
                                                // Rectangle rect = new Rectangle(startX, startY, width, height);
                                                // content.addRect(rect.x, rect.y, rect.width, rect.height);
                                                float width = endToken.getX() - startToken.getX();
                                                float height = startToken.getHeightDir();
                                                float scaleDiff = (height * scaleHeight) - height;
                                                Rectangle rect = new Rectangle(
                                                        (int)Math.ceil(startToken.getX()),
                                                        (int)Math.ceil(startToken.getY() - (scaleDiff / 2.0f)), 
                                                        (int)Math.ceil(width * scaleWidth),
                                                        (int)Math.ceil(height * scaleHeight)
                                                );
                                                System.out.println("Rect: " + rect.toString());

                                                System.out.println("Page: " + super.getCurrentPageNo());

                                                redactionRects.add(rect);

                                                // writeString("[x: " + fp.getXDirAdj() + ", y: "
                                                //         + fp.getY() + ", height:" + fp.getHeightDir()
                                                //         + ", space: " + fp.getWidthOfSpace() + ", width: "
                                                //         + fp.getWidthDirAdj() + ", yScale: " + fp.getYScale() + "]");


                                                // TODO: add to redaction areas list
                                                startIndex = matchEndIndex + 1;
                                        }
                                }

                                if (startOfLine)
                                {
                                        // first position
                                        TextPosition fp = textPositions.get(0);
                                        // // writeString(String.format("[%s]", fp.getXDirAdj()));
                                        // writeString("[x: " + fp.getXDirAdj() + ", y: "
                                        //         + fp.getY() + ", height:" + fp.getHeightDir()
                                        //         + ", space: " + fp.getWidthOfSpace() + ", width: "
                                        //         + fp.getWidthDirAdj() + ", yScale: " + fp.getYScale() + "]");
                                        startOfLine = false;
                                }
                                super.writeString(text, textPositions);
                        }

                        // @Override
                        // protected void processTextPosition(TextPosition text) {
                        //         String c = text.toString();
                        //         if (word.isEmpty()) {
                        //                 startOfWord = text;
                        //         }
                        //         if (c.equals(super.getWordSeparator())) {
                        //                 System.out.println("WORD: " + word);
                        //                 endOfWord = text;
                        //                 wordTokens.clear();
                        //                 word = "";
                        //         } else {
                        //                 word += c;
                        //                 System.out.println("String[" + text.getXDirAdj() + ","
                        //                                 + text.getYDirAdj() + " fs=" + text.getFontSize() + " xscale="
                        //                                 + text.getXScale() + " height=" + text.getHeightDir() + " space="
                        //                                 + text.getWidthOfSpace() + " width="
                        //                                 + text.getWidthDirAdj() + "]" + c);
                        //                 wordTokens.add(text);
                        //         }
                        //         super.processTextPosition(text);
                        // }
                        // scale the height and width by this percent
                        float scaleWidth = 1.10f;
                        // height is particular bad, we need to make sure this really covers the area
                        float scaleHeight = 1.75f;
                        boolean startOfLine = true;
                        TextPosition startOfWord;
                        TextPosition endOfWord;
                        String word = "";
                        List<TextPosition> wordTokens = new ArrayList<TextPosition>();
                };
        
                // page number is 1 indexed I guess?
                stripper.setStartPage(pageNumber+1);
                stripper.setEndPage(pageNumber+1);
                System.out.println("Getting rects for page: " + pageNumber);
                stripper.getText(document);
                System.out.println("Total redaction rects: " + redactionRects.size());
                // System.out.println("STRIPPED\n" + stripper.getText(document));

                PDPageContentStream content = new PDPageContentStream(document, page, true, true);
                for(Rectangle rect: redactionRects) {
                        System.out.println("Redacting rect: " + rect.toString());
                        rect.y = (int)page.getMediaBox().getHeight() - rect.y;
                        drawRect(content, rect, Color.BLACK, true);
                }
                System.out.println("Page " + pageNumber + " done.");

                content.close();

            /*
            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.setSortByPosition(true);
            stripper.extractRegions(page);
            // Get the characters and their locations
            String searchedChar = "a";
            List<List<TextPosition>> vectorlistoftps = stripper.getCharactersByArticle();
            for (int ii = 0; ii < vectorlistoftps.size(); ii++) {
                    List<TextPosition> tplist = vectorlistoftps.get(ii);
                    for (int jj = 0; jj < tplist.size(); jj++) {
                            TextPosition text = tplist.get(jj);
                            System.out.println(" String "
                                            + "[x: " + text.getXDirAdj() + ", y: "
                                            + text.getY() + ", height:" + text.getHeightDir()
                                            + ", space: " + text.getWidthOfSpace() + ", width: "
                                            + text.getWidthDirAdj() + ", yScale: " + text.getYScale() + "]");
                    }
            }
            */

            PDFStreamParser parser = new PDFStreamParser(page);
            parser.parse();
            List tokens = parser.getTokens();

            for (int j = 0; j < tokens.size(); j++)
            {
                Object next = tokens.get(j);
                if (next instanceof Operator)
                {
                    Operator op = (Operator) next;

                    String pstring = "";
                    int prej = 0;

                    //Tj and TJ are the two operators that display strings in a PDF
                    if (op.getName().equals("Tj"))
                    {
                        // Tj takes one operator and that is the string to display so lets update that operator
                        COSString previous = (COSString) tokens.get(j - 1);
                        String string = previous.getString();
                        if (string.equals(searchString)) {
                                System.out.println("MATCH");
                        }
                        string = string.replaceFirst(searchString, replacement);
                        previous.setValue(string.getBytes());
                    }
		    else if (op.getName().equals("TJ"))
                    {
                        COSArray previous = (COSArray) tokens.get(j - 1);
                        for (int k = 0; k < previous.size(); k++)
                        {
                            Object arrElement = previous.getObject(k);
                            if (arrElement instanceof COSString)
                            {
                                COSString cosString = (COSString) arrElement;
                                String string = cosString.getString();

                                if (j == prej) {
                                    pstring += string;
                                } else {
                                    prej = j;
                                    pstring = string;
                                }
                            }
                        }

                        // word match here
                        if (searchString.equals(pstring.trim()))
                        {
                            COSString cosString2 = (COSString) previous.getObject(0);
                            cosString2.setValue(replacement.getBytes());
                            int total = previous.size()-1;
                            for (int k = total; k > 0; k--) {
                                previous.remove(k);
                            }
                        }
                    }
                }
            }

            // now that the tokens are updated we will replace the page content stream.
            PDStream updatedStream = new PDStream(document);
            OutputStream out = updatedStream.createOutputStream(COSName.FLATE_DECODE);
            ContentStreamWriter tokenWriter = new ContentStreamWriter(out);
            tokenWriter.writeTokens(tokens);


            out.close();
            page.setContents(updatedStream);


        }

        return document;
    }

    /**
     * This will print the usage for this document.
     */
    private static void usage()
    {
        System.err.println( "Usage: java " + App.class.getName() + " <old-text> <input-pdf> <output-pdf>" );
    }

}
