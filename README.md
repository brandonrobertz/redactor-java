# Redactor Java

This is a quick and dirty Java [PDFBox][pdfbox] based redaction tool for removing things like PII and phone numbers from a PDF.

It's still a work in progress, and the [regexes][regexes] it uses probably will miss some stuff, but this should serve as a good baseline to start with.

## Quickstart

Clone this, make sure you have java and maven on your path then run:

    mvn package
    java -jar target/App-uber.jar
    # Usage: java org.bxroberts.App <pattern> <input-pdf> <output-pdf>


[regexes]: https://github.com/brandonrobertz/redactor-java/blob/main/src/main/java/org/bxroberts/App.java#L169-L174
    "The regexes in the source code"

[pdfbox]: https://pdfbox.apache.org/
    "Apache PDFBox® - A Java PDF Library"
