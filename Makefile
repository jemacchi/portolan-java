.PHONY: help setup test coverage check build install clean

MVN ?= mvn

help:
	@printf '%s\n' 'Targets: setup test coverage check build install clean'

setup:
	$(MVN) -q -DskipTests test

test:
	$(MVN) test

coverage:
	$(MVN) verify

check:
	$(MVN) verify

build:
	$(MVN) package

install:
	$(MVN) install

clean:
	$(MVN) clean
