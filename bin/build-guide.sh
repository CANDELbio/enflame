emacs --script doc/build-guide.el

# this isn't working on circleci due to problems loading the emacs package, so turned off
# emacs --script doc/jekyll-build-guide.el


# Alternative if the above stops working
# cd doc; pandoc guide.org -s -o guide.html
