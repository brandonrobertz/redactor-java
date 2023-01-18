# Redactor Java

This is a quick and dirty Java [PDFBox][pdfbox] based redaction tool for removing things like PII and phone numbers from a PDF.

It's still a work in progress, and the regexes it has probably will miss some stuff, but this should serve as a good baseline to start with.

## Quickstart

Clone this, make sure you have java and maven on your path then run:

    mvn package
    java -jar target/App-uber.jar
    # Usage: java org.bxroberts.App <pattern> <input-pdf> <output-pdf>
