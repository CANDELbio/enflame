cd doc
pandoc guide.org -s -o guide.html -M document-css=false -V include-before="<div class='container mw-80 mh-100 h-100'>" -V include-after="</div>"
pandoc tutorial.org -s -o tutorial.html -M document-css=false -V include-before="<div class='container mw-80 mh-100 h-100'>" -V include-after="</div>"
