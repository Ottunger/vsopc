#Generating files for binary(ies)

all: install-tools vsopc

install-tools:
	echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | sudo tee /etc/apt/sources.list.d/webupd8team-java.list
	echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | sudo tee -a /etc/apt/sources.list.d/webupd8team-java.list
	echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections
	sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886
	sudo apt-get update
	sudo apt-get install -y oracle-java8-installer
	wget http://llvm.org/releases/3.2/llvm-3.2.src.tar.gz
	tar -xf llvm-3.2.src.tar.gz
	cd llvm-3.2.src; ./configure --enable-shared; sudo make CXXFLAGS='-D__STDC_CONSTANT_MACROS -D__STDC_LIMIT_MACROS' CFLAGS='-D__STDC_CONSTANT_MACROS -D__STDC_LIMIT_MACROS'; sudo make install
	
vsopc:
	find -name "*.java" > sources
	echo "Main-Class: be.ac.ulg.vsop.Compiler" >> manifest
	javac -classpath ./build -d ./build @sources
	cd build; jar cvfm ../vsopcompiler.jar ../manifest ./*
	echo "#! /bin/sh" > vsopc
	echo "DIR=\$$(dirname "\$$0")" >> vsopc
	echo "java -jar \"\$$DIR/vsopcompiler.jar\" \"\$$@\" " >> vsopc
	chmod 755 vsopc vsopcompiler.jar
	rm -rf manifest
	
#Clean workspace

clean:
	rm -rf manifest