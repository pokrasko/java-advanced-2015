LIBDIR = info/kgeorgiy/java/advanced/implementor
LIBS_ = ImplerException Impler JarImpler
LIBS = $(patsubst %,$(LIBDIR)/%.class,$(LIBS_))

SRCDIR = ru/ifmo/ctddev/nekrasov/implementor
SOURCES_ = Implementor Main
SOURCES = $(patsubst %,$(SRCDIR)/%.class,$(SOURCES_))

MANIFEST = META-INF/MANIFEST.MF

FILES = $(patsubst %,bin/%,$(LIBS) $(SOURCES))

OUT = Implementor.jar

all: $(OUT)

clean:
	rm -rf bin
	mkdir bin
	rm -r $(OUT)

$(OUT): $(MANIFEST) $(FILES)
	cd bin && jar -cfm $@ ../$< ru info && cp $@ .. && cd ..

bin/%.class: %.java
	javac -d bin $<
