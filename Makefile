# I am sorry, didn't have time to learn gradle yet


VERSION=1.0
XLINT=-Xlint
CLASSPATH= ../bitcoinj/bitcoinj-core-0.14.3-bundled.jar:./:../slf4g/slf4j-1.7.22/slf4j-api-1.7.22.jar
LIBS=../bitcoinj/bitcoinj-core-0.14.3-bundled.jar ../slf4g/slf4j-1.7.22/slf4j-simple-1.7.22.jar *.class
TARGET=BlockMagicBox.class

default: all

%.class: %.java
	javac $(XLINT) -classpath $(CLASSPATH) $^

all: $(TARGET)

.PHONY: clean start

start: 
	java -classpath ../bitcoinj/bitcoinj-core-0.14.3-bundled.jar:./:../slf4g/slf4j-1.7.22/slf4j-simple-1.7.22.jar BlockMagicBox mjYfd4khBukNe5NwgUQ1iRZ4hJrRutpAtz testnet

clean: 
	rm -f *~ *.class *.tmp $(TARGET)
