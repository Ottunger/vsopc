#Generating files for binary(ies)

all: install-tools vsopc

install-tools:
	echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | sudo tee /etc/apt/sources.list.d/webupd8team-java.list
	echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | sudo tee -a /etc/apt/sources.list.d/webupd8team-java.list
	echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections
	sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886
	sudo apt-get update
	sudo apt-get install -y oracle-java8-installer
	sudo apt-get install -y clang
	wget http://hboehm.info/gc/gc_source/gc-7.2f.tar.gz
	tar -xf gc-7.2f.tar.gz
	cd gc-7.2; ./configure --prefix=/usr/local/gc --disable-threads; make; sudo make install
	rm -rf gc-*
	sudo cp -f generics.ve /usr/local/gc/
	
vsopc:
	find -name "*.java" > sources
	echo "Main-Class: be.ac.ulg.vsop.Compiler" >> manifest
	javac -classpath ./build -d ./build @sources > /dev/null
	cd build; jar cvfm ../vsopcompiler.jar ../manifest ./* > /dev/null
	echo "#! /bin/sh" > vsopc
	echo "DIR=\$$(dirname "\$$0")" >> vsopc
	echo "java -jar \"\$$DIR/vsopcompilero.jar\" \"\$$@\" " >> vsopc
	chmod 755 proguard.jar
	java -jar proguard.jar -injars vsopcompiler.jar -outjars vsopcompilero.jar -libraryjars /usr/lib/jvm/java-8-oracle/jre/lib/rt.jar -keep class be.ac.ulg.vsop.Compiler {public static void main\(java.lang.String[]\)\;}
	chmod 755 vsopc vsopcompilero.jar
	rm -rf manifest
	
#Clean workspace

clean:
	rm -rf manifest