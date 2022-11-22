;; Publish for candel-website

(package-initialize)
(package-install 'ox-jekyll-md)
(require 'ox-jekyll-md)


;;; ∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘

;;; This section is a patch modification to https://github.com/gonsie/ox-jekyll-md/blob/master/ox-jekyll-md.el
;;; And could even be packaged into a PR for it.


;;; Identical to org-jekyll-md-publish-to-md except for extension
(defun org-jekyll-md-publish-to-markdown (plist filename pub-dir)
  "Publish an org file to Markdown with YAML front matter.

FILENAME is the filename of the Org file to be published.  PLIST
is the property list for the given project.  PUB-DIR is the
publishing directory.

Return output file name."
  (org-publish-org-to 'jekyll filename ".markdown" plist pub-dir))

(defun convert-to-yaml-list (arg)
  (mapconcat #'(lambda (text)(concat "\n- " text))
	     (split-string arg) " "))

(defvar org-jekyll-md--yaml-front-matter-fields
  '((title (lambda (info)
	     (concat "\"" (org-jekyll-md--get-option info :title) "\"")))
    (layout (lambda (info)
	      (org-jekyll-md--get-option info :jekyll-layout org-jekyll-md-layout)))
    (catgories (lambda (info)
		 (convert-to-yaml-list
		  (org-jekyll-md--get-option
		   info
		   :jekyll-categories org-jekyll-md-categories))) )
    (tags (lambda (info)
	    (convert-to-yaml-list
	     (org-jekyll-md--get-option
	      info
	      :jekyll-tags org-jekyll-md-tags))))
    (date (lambda (info)
	    (and (plist-get info :with-date)
		 (org-jekyll-md--get-option info :date))))))


;;; Adds the nav_order field, 
(defun org-jekyll-md--yaml-front-matter (info)
  (setq the-info info)
  "Creat YAML frontmatter content."
  (concat "---"
	 (mapconcat #'(lambda (entry)
		    (concat "\n" (symbol-name (car entry)) ": "
			    (funcall (cadr entry) info)))
		    org-jekyll-md--yaml-front-matter-fields
		    "")
	 "\n---\n"))

;;; End patch

;;; ∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘∮⨘





(nconc org-jekyll-md--yaml-front-matter-fields
       '((nav_order (lambda (info)
		      (prin1-to-string 7)))))

(setq org-publish-project-alist
  '(("jguide"
     :base-directory "doc/"
     :publishing-directory "doc/candel-website/"
;     :publishing-directory "../candel-website/docs/enflame/"
     :publishing-function org-jekyll-md-publish-to-markdown
     )))

(org-publish "jguide" t)

;;; Need to hand-edit a few links from relative to absolute
;;; Also add nav_order: 7 to header




