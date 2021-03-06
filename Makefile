SRCDIR = src/mjw297
PKG = mjw297
PARSER_CLASS = Parser
INTERFACE_PARSER_CLASS = InterfaceParser
SYMBOL_CLASS = Sym
LEXER_CLASS = Lexer
PARSER = $(SRCDIR)/$(PARSER_CLASS)
INTERFACE_PARSER = $(SRCDIR)/$(INTERFACE_PARSER_CLASS)
SYMBOL = $(SRCDIR)/$(SYMBOL_CLASS)
LEXER  = $(SRCDIR)/$(LEXER_CLASS)
CUPJAR = lib/java_cup.jar
FLEXJAR = lib/jflex-1.6.1.jar
SRCS  = $(shell find src -name '*.java') \
        $(shell find test -name '*.java') \
	    $(LEXER).java \
	    $(PARSER).java \
	    $(INTERFACE_PARSER).java \
	    $(SYMBOL).java
TESTS = $(shell find test -name '*Test.java' \
            | sed 's/.java//' \
		    | sed 's_test/__' \
			| tr '/' '.')
LIBS  = $(shell ls lib/*.jar | tr '\n' ':' | sed 's/:$$//')
CP    = $(LIBS):$$CLASSPATH
BIN   = bin
DOC   = doc
COVERAGE = coverage
JAVAC_FLAGS = -Xlint
JAVADOC_FLAGS = -Xdoclint:all,-missing

OCAML_SUFFIX=byte
OCAML_MAIN  = src/ocaml/main
OCAML_SRCS  = $(OCAML_MAIN).ml src/ocaml/xi.ml
OCAML_TESTS = $(shell find test -name '*Test.ml')
OCAML_SRCS_BIN  = $(OCAML_SRCS:.ml=.$(OCAML_SUFFIX))
OCAML_TESTS_BIN = $(OCAML_TESTS:.ml=.$(OCAML_SUFFIX))
OCAML_TESTS_EXE = $(notdir $(OCAML_TESTS_BIN))
BISECT = ""

default: clean src test doc publish

$(LEXER).java: $(LEXER).jflex
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	java -jar $(FLEXJAR) $<

$(PARSER).java $(SYMBOL).java: $(PARSER).cup
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	java  -cp $(CUPJAR):$(CP) java_cup.Main \
		                -destdir $(SRCDIR) \
		                -package $(PKG) \
						-parser $(PARSER_CLASS) \
						-symbols $(SYMBOL_CLASS) \
						-nowarn \
						$<

$(INTERFACE_PARSER).java: $(INTERFACE_PARSER).cup
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	java  -cp $(CUPJAR):$(CP) java_cup.Main \
		                -destdir $(SRCDIR) \
		                -package $(PKG) \
						-parser $(INTERFACE_PARSER_CLASS) \
						-symbols $(SYMBOL_CLASS) \
						-nowarn \
						$<

.PHONY: src
src: $(SRCS) $(OCAML_SRCS_BIN) $(OCAML_TESTS_BIN)
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	mkdir -p $(BIN) && javac $(JAVAC_FLAGS) -d $(BIN) -cp $(CP) $(SRCS)
	@echo

%.$(OCAML_SUFFIX): %.ml
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	# corebuild -pkgs async,oUnit,ocamlgraph,bisect_ppx
	corebuild -pkgs async,oUnit,ocamlgraph$$(if [ $(BISECT) ]; then echo ",bisect_ppx"; fi) \
			  -Is   src/ocaml,test $@ \
			  -cflags -warn-error,+a
	mkdir -p $(BIN)
	mv -f $(notdir $@) $(BIN) || true
	rm $(notdir $@) || true

.PHONY: doc
doc:
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	mkdir -p $(DOC) && javadoc $(JAVADOC_FLAGS) -d $(DOC) -cp $(CP) $(SRCS)
	@echo

.PHONY: coverage
coverage:
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	rm -rf $(COVERAGE) && mkdir -p $(COVERAGE) && bisect-report \
		-I _build/ \
		-verbose \
		-html $(COVERAGE)/ \
		bisect*.out
	@echo

.PHONY: test
test: src java_test ocaml_test frontend_test difftest
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"

.PHONY: java_test
java_test:
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	java -cp $(BIN):$(CP) org.junit.runner.JUnitCore $(TESTS)
	@echo

.PHONY: ocaml_test
ocaml_test: $(OCAML_SRCS_BIN) $(OCAML_TESTS_BIN)
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	@for t in $(OCAML_TESTS_EXE); do \
		echo $$t; \
		./$(BIN)/$$t || exit 1; \
		echo ""; \
    done
	@echo

.PHONY: frontend_test
frontend_test: frontend_test.sh
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	./frontend_test.sh
	@echo

.PHONY: difftest
difftest: difftest.sh
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	./difftest.sh xisrc/ours/difftests/*
	@echo

.PHONY: publish
publish: coverage
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	# http://stackoverflow.com/a/677212/3187068
	if command -v ghp-import >/dev/null 2>&1; then \
		ghp-import -n coverage; \
		git push -f origin gh-pages; \
	else \
		echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"; \
		echo "! ghp-import not installed; docs will not be published             !"; \
		echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"; \
	fi
	@echo

.PHONY: clean
clean:
	@echo "********************************************************************"
	@echo "* make $@"
	@echo "********************************************************************"
	rm -rf $(BIN)
	rm -rf $(DOC)
	rm -rf $(PARSER).java
	rm -rf $(INTERFACE_PARSER).java
	rm -rf $(SYMBOL).java
	rm -rf $(LEXER).java
	rm -f  bisect*.out
	rm -f  p1.zip
	rm -f  p2.zip
	rm -f  p3.zip
	rm -f  p4.zip
	corebuild -clean
	@echo

p1.zip: clean $(LEXER).java $(SYMBOL).java $(PARSER).java $(INTERFACE_PARSER).java src lib xic bin test doc Makefile README.md vagrant xic-build
	zip -r $@ $^

p2.zip: clean $(LEXER).java $(SYMBOL).java $(PARSER).java $(INTERFACE_PARSER).java src lib xic bin test doc Makefile README.md vagrant xic-build
	zip -r $@ $^

p3.zip: clean $(LEXER).java $(SYMBOL).java $(PARSER).java $(INTERFACE_PARSER).java src lib xic bin test doc Makefile README.md vagrant xic-build
	zip -r $@ $^

p4.zip: clean src ir irtest lib xic bin/edu bin/mjw297/ bin/polyglot/  test Makefile README.md vagrant xic-build
	zip -r $@ $^

p5.zip: clean src ir xisrc/ xilib/ lib xi xic bin/edu bin/mjw297/ bin/polyglot/ opam Makefile README.md vagrant xic-build difftest.sh frontend_test.sh
	zip -r $@ $^

p6.zip: clean src ir xisrc/ xilib/ lib xi xic bin/edu bin/mjw297/ bin/polyglot/ opam Makefile README.md vagrant xic-build difftest.sh frontend_test.sh benchmarks/ test/
	zip -r $@ $^

print-%:
	@echo $*=$($*)
