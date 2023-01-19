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
 * Redact areas of a PDF based on regex matches.
 *
 * NOTE: This doesn't remove the actual text, just places a bounding box over the text.
 *
 * Super inspiration: https://stackoverflow.com/questions/32978179/using-pdfbox-to-get-location-of-line-of-text
 * Inspiration: https://github.com/chadilukito/Apache-PdfBox-2-Examples/blob/master/ReplaceText.java
 */
public final class App
{
    /**
     * Regexes to match and place a bounding box over.
     */
    public static final String[] regexes = {
        // email
        "[a-zA-Z0-9.]+@[a-zA-Z0-9.]+",
        // phone 0300 918 8111
        "\\d+\\s\\d+\\s\\d+\\s\\d*\\s*\\d*\\s*",
        // advanced email
        "[a-z0-9!#$%&'*+\\/=?^_`{|.}~-]+@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?",
        // phone
        "((?:(?<![[0-9]-])(?:\\+?[0-9]{1,3}[-.\\s*]?)?(?:\\(?[0-9]{3}\\)?[-.\\s*]?)?[0-9]{3}[-.\\s*]?[0-9]{4}(?![[0-9]-]))|(?:(?<![[0-9]-])(?:(?:\\(\\+?[0-9]{2}\\))|(?:\\+?[0-9]{2}))\\s*[0-9]{2}\\s*[0-9]{3}\\s*[0-9]{4}(?![[0-9]-])))",
        // BITCOIN_ADDRESS
        "(?<![a-km-zA-HJ-NP-Z0-9])[13][a-km-zA-HJ-NP-Z0-9]{26,33}(?![a-km-zA-HJ-NP-Z0-9])",
        // CREDIT_CARD
        "((?:(?:[0-9]{4}[- ]?){3}[0-9]{4}|[0-9]{15,16}))(?![[0-9]])",
        // SOCIAL_SECURITY_NUMBER
        "(?!000|666|333)0*(?:[0-6][0-9][0-9]|[0-7][0-6][0-9]|[0-7][0-7][0-2])[- ](?!00)[0-9]{2}[- ](?!0000)[0-9]{4}",
    };

    /**
     * Default constructor.
     */
    private App() {}

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
        String replacement = "";

        String infile = args[1];
        String outfile = args[2];

        for (int i = 0; i < searchString.length(); i++)
        {
            replacement += "*";
        }

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

    private static void drawRect(PDPageContentStream content, Rectangle rect, Color color, boolean fill) throws IOException
    {
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
        if (StringUtils.isEmpty(searchString) || StringUtils.isEmpty(replacement))
        {
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
                protected void writeString(String text, List<TextPosition> textPositions) throws IOException
                {
                    String textString = "";
                    for(TextPosition t: textPositions) {
                        String chr = t.toString();
                        if (chr.length() < 1) chr = " ";
                        textString += chr;
                    }

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

                    // minimum height of a redaction
                    float minRedactHeight = 7.0f;
                    for(String match: matches) {
                        int startIndex = 0;
                        int matchStartIndex  = -1;
                        int matchEndIndex = -1;
                        while ((matchStartIndex = textString.indexOf(match, startIndex)) > -1)
                        {
                            // get index of match start and end
                            matchEndIndex = matchStartIndex + match.length();
                            matchEndIndex = (matchEndIndex > textPositions.size()) ? textPositions.size() : matchEndIndex;
                            System.out.println("textString: " + textString
                                    + " length: " + textString.length());
                            System.out.println(" match: " + match
                                    + " matchEndIndex: " + matchEndIndex
                                    + " matchStartIndex: " + matchStartIndex);
                            // get token of match start
                            TextPosition startToken = textPositions.get(matchStartIndex);
                            // get last token
                            TextPosition endToken = textPositions.get(matchEndIndex - 1);
                            // compute bounding box
                            float width = endToken.getX() - startToken.getX();
                            float height = startToken.getHeightDir();
                            height = (height < minRedactHeight) ? minRedactHeight : height;
                            float scaleDiff = Math.abs((height * scaleHeight) - height);
                            Rectangle rect = new Rectangle(
                                (int)Math.ceil(startToken.getX()),
                                // make sure we cover the bottoms of the chars, shift
                                // down a little bit to do so (positive moves down on page here)
                                (int)Math.ceil(startToken.getY() + (scaleDiff * 0.25)), 
                                (int)Math.ceil(width * scaleWidth),
                                (int)Math.ceil(height * scaleHeight)
                            );

                            System.out.println("Rect: " + rect.toString());
                            System.out.println("Page: " + super.getCurrentPageNo());

                            redactionRects.add(rect);
                            startIndex = matchEndIndex;
                        }
                    }
                    super.writeString(text, textPositions);
                }

                // scale the height and width by this percent
                float scaleWidth = 1.25f;
                // height is particular bad, we need to make sure this really covers the area
                float scaleHeight = 2.0f;
            };

            // page number is 1 indexed I guess?
            stripper.setStartPage(pageNumber+1);
            stripper.setEndPage(pageNumber+1);
            System.out.println("Getting rects for page: " + pageNumber);
            stripper.getText(document);
            System.out.println("Total redaction rects: " + redactionRects.size());

            // Actually draw the rects
            PDPageContentStream content = new PDPageContentStream(document, page, true, true);
            for(Rectangle rect: redactionRects) {
                System.out.println("Redacting rect: " + rect.toString());
                rect.y = (int)page.getMediaBox().getHeight() - rect.y;
                drawRect(content, rect, Color.BLACK, true);
            }
            System.out.println("Page " + pageNumber + " done.");
            content.close();

            /**
             * NOTE: The rest of this doesn't really work on most PDFs.
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
                            for (int k = total; k > 0; k--)
                            {
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
