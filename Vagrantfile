# coding: utf-8
# -*- mode: ruby -*-
# vi: set ft=ruby :

# This is a Vagrant file (https://docs.vagrantup.com/v2/).  Use this
# to get a standard development environment for the ABS tools.

# To get started, install vagrant
# (https://www.vagrantup.com/downloads.html) and VirtualBox
# (https://www.virtualbox.org/wiki/Downloads).  Then, from this
# directory, run "vagrant up".  When run the first time, this command
# will download and install an ABS environment; subsequent invocations
# will be much faster.

# To use the tools, execute "vagrant up" then "vagrant ssh".

# For running graphical programs from inside the VM (eclipse,
# key-abs), you will need an X server installed: XQuartz
# (http://xquartz.macosforge.org) for OS X or Xming
# (http://sourceforge.net/projects/xming/) for Windows.

# If you want to modify the installed software, edit the
# "config.vm.provision" at the end of this file.

# Vagrantfile API/syntax version. Don't touch unless you know what you're doing!
VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  # All Vagrant configuration is done here.  For a complete reference,
  # please see the online documentation at
  # https://docs.vagrantup.com/v2/

  config.vm.box = "ubuntu/wily64"
  config.vm.network "forwarded_port", guest: 80, host: 8888

  config.vm.post_up_message = <<-MSG
Welcome to the ABS toolchain VM.

Connect to the collaboratory at http://localhost:8888/

Access the following additional tools with 'vagrant ssh'
(install Xming / XQuartz X server on Windows/Mac to use):

- eclipse          Eclipse Mars with ABS plugins
- key-abs          Deductive verification tool
- emacs            Emacs with ABS mode
MSG

  config.ssh.forward_x11 = true

  config.vm.provider "virtualbox" do |vb|
    vb.memory = 4096
    vb.cpus = 2
    vb.name = "ABS tools VM (Vagrant)"
    vb.customize ["modifyvm", :id, "--natdnshostresolver1", "on"] # fix for ubuntu DNS problems
  end

  # Install necessary software
  config.vm.provision "shell",
                      privileged: false,
                      inline: <<-SHELL

echo
echo "Installing necessary tools for the ABS compiler"
echo
sudo apt-get update -y -q
sudo apt-get -y -q install software-properties-common htop
sudo apt-get -y -q install openjdk-8-jdk openjdk-8-jre
sudo apt-get install -y -q ant antlr junit git unzip
sudo apt-get install -y -q erlang

echo
echo "Installing necessary tools for simulating ABS programs"
echo
sudo apt-get install -y -q emacs maude graphviz

echo
echo "Downloading eclipse, this might take a while ..."
echo
wget -nv "http://www.eclipse.org/downloads/download.php?file=/technology/epp/downloads/release/mars/1/eclipse-rcp-mars-1-linux-gtk-x86_64.tar.gz&r=1" -O eclipse-rcp-mars-1-linux-gtk-x86_64.tar.gz
echo "Installing eclipse in /opt/eclipse and setting up paths ..."
(cd /opt && sudo tar xzf /home/vagrant/eclipse-rcp-mars-1-linux-gtk-x86_64.tar.gz)
sudo ln -s /opt/eclipse/eclipse /usr/local/bin/eclipse
rm /home/vagrant/eclipse-rcp-mars-1-linux-gtk-x86_64.tar.gz

echo
echo "Building the ABS compiler and eclipse plugins"
echo
(cd /vagrant/eclipse-plugin ; ant -Declipse.home=/opt/eclipse build-all-plugins generate-update-site)

echo
echo "Deploying to eclipse"
echo
eclipse -application org.eclipse.equinox.p2.director -noSplash \
        -repository \
file:/vagrant/eclipse-plugin/update-site,\
http://download.eclipse.org/releases/mars/ \
-installIUs \
org.abs-models.costabs.feature.group,\
org.abs-models.apet.feature.group,\
org.abs-models.abs.compiler.feature.group,\
org.abs-models.sda.feature.group,\
org.abs-models.abs.plugin,\
org.abs-models.sdedit.feature.group


echo
echo "Downloading KeY-ABS, this might take a while..."
wget -q http://www.key-project.org/key-abs/key-abs.zip
(cd /usr/local/lib && sudo unzip -o /home/vagrant/key-abs.zip)
rm key-abs.zip
cat >key-abs <<EOF
#!/bin/sh
java -jar /usr/local/lib/key-abs/key.jar "\\$@"
EOF
sudo mv key-abs /usr/local/bin
sudo chown root.root /usr/local/bin/key-abs
sudo chmod a+x /usr/local/bin/key-abs
# work around bug in key-abs: it doesn't create the directory it requires
mkdir -p /home/vagrant/.key

echo
echo "Installing SACO command-line tool"
echo
wget -q http://costa.ls.fi.upm.es/download/saco.colab.zip
(cd /usr/local/lib && sudo unzip -o /home/vagrant/saco.colab.zip)
rm saco.colab.zip

# workaround for re-used temporary directory: need to be writable
# by users www-data (for easyinterface) and vagrant (for commandline)
mkdir -p /tmp/costabs/absPL
sudo chown -R www-data.www-data /tmp/costabs
sudo chmod -R 777 /tmp/costabs

echo
echo "Installing COFLOCO and SRA"
echo
wget -q http://costa.ls.fi.upm.es/download/cofloco.colab.zip
(cd /usr/local/lib && sudo unzip -o /home/vagrant/cofloco.colab.zip)
rm cofloco.colab.zip
wget -q http://costa.ls.fi.upm.es/download/sra.colab.zip
(cd /usr/local/lib && sudo unzip -o /home/vagrant/sra.colab.zip)
rm sra.colab.zip

echo
echo "Setting up apache and easyinterface"
echo
sudo apt-get -y -q install apache2 apache2-utils openssl-blacklist
sudo apt-get -y -q install php5 libapache2-mod-php5 php5-mcrypt
sudo rm -rf /var/www/absexamples
(cd /var/www && sudo git clone https://github.com/abstools/absexamples.git)
sudo chmod -R 755 /var/www/absexamples
sudo rm -rf /var/www/easyinterface
(cd /var/www && sudo git clone https://github.com/abstools/easyinterface.git)
(cd /var/www/easyinterface/server/config/envisage && ./offlineabsexamples.sh /var/www/absexamples > /home/vagrant/examples.cfg)
sudo mv /home/vagrant/examples.cfg /var/www/easyinterface/server/config/envisage
sudo chown root.root /var/www/easyinterface/server/config/envisage/examples.cfg
sudo chmod -R 755 /var/www/easyinterface

# Set up apache2
cat >/home/vagrant/easyinterface-site.conf <<EOF
Alias /ei "/var/www/easyinterface"

<Directory "/var/www/easyinterface">
   Options FollowSymlinks MultiViews Indexes IncludesNoExec
   AllowOverride All
   Require all granted
</Directory>

Alias /absexamples "/var/www/absexamples"

<Directory "/path-to/absexamples">
   Options FollowSymlinks MultiViews Indexes IncludesNoExec
   AllowOverride All
   Require all granted
</Directory>
EOF
cat >/home/vagrant/index.html <<EOF
<html><head>
<META HTTP-EQUIV="Refresh" Content="0; URL=/ei/clients/web">
</head><body>
EasyInterface is at http://localhost:8888/ei/clients/web.
</body></html>
EOF
sudo mv index.html /var/www/html
sudo chown root.root /var/www/html/index.html
sudo mv /home/vagrant/easyinterface-site.conf /etc/apache2/sites-available/
sudo chown root.root /etc/apache2/sites-available/easyinterface-site.conf
sudo a2ensite easyinterface-site
sudo a2enmod headers
sudo service apache2 restart

cat >ENVISAGE_CONFIG <<EOF
# path to saco
EC_SACOHOME="/usr/local/lib/saco/"
# path to abs tools
EC_ABSTOOLSHOME="/vagrant/"
# path to COFLOCO
EC_COFLOCOHOME="/usr/local/lib/cofloco/"
# path to SRA jar
EC_SRAHOME="/usr/local/lib/sra/"
EOF
sudo mv ENVISAGE_CONFIG /var/www/easyinterface/server/bin/envisage/ENVISAGE_CONFIG
sudo chown root.root /var/www/easyinterface/server/bin/envisage/ENVISAGE_CONFIG
(cd /var/www/easyinterface/server/config ; sudo cp envisage.cfg eiserver.cfg)
(cd /var/www/easyinterface/clients/web ; sudo cp envisage.cfg webclient.cfg)

echo
echo "Setting up the user environment: .bashrc, .emacs"
echo

# Set up Emacs
if [ ! -e /home/vagrant/.emacs ] ; then
cat >/home/vagrant/.emacs <<EOF
;; Set up ABS, Maude.  Added by Vagrant provisioning
(add-to-list 'load-path "/vagrant/emacs")
(require 'abs-mode)
(add-to-list 'auto-mode-alist (cons "\\\\.abs\\\\'" 'abs-mode))
(require 'maude-mode)
(add-to-list 'auto-mode-alist '("\\\\.maude\\\\'" maude-mode))
EOF
fi

# Set up paths
cat >/home/vagrant/.abstoolsrc <<EOF
PATH=\$PATH:/opt/ghc/7.8.4/bin:/opt/cabal/1.20/bin:/opt/alex/3.1.3/bin:/opt/happy/1.19.4/bin:/vagrant/abs2haskell/.cabal-sandbox/bin:/vagrant/frontend/bin/bash:/vagrant/costabs-plugin:/usr/local/lib/saco/bin
# used by the costabs executable
export COSTABSHOME=/usr/local/lib/saco/
# used by the costabs executable
export ABSFRONTEND=/vagrant/frontend/dist/absfrontend.jar
export GHC_PACKAGE_PATH=/vagrant/abs2haskell/.cabal-sandbox/x86_64-linux-ghc-7.8.4-packages.conf.d:/opt/ghc/7.8.4/lib/ghc-7.8.4/package.conf.d:/home/vagrant/.ghc/x86_64-linux-7.8.4/package.conf.d
EOF

if [ -z "$(grep abstoolsrc /home/vagrant/.bashrc)" ] ; then
cat >>/home/vagrant/.bashrc <<EOF
. .abstoolsrc
EOF
fi

echo
echo "Installing Haskell"
echo

sudo add-apt-repository ppa:hvr/ghc
sudo apt-get update -y -q
sudo apt-get install -y -q ghc-7.8.4 cabal-install-1.20 happy-1.19.4 alex-3.1.3 zlib1g-dev

echo
echo "Building the ABS-Haskell compiler"
echo

git clone http://github.com/bezirg/abs2haskell
cd abs2haskell
git checkout cloud
git submodule init
git submodule update

export PATH=$PATH:/opt/cabal/1.20/bin:/opt/ghc/7.8.4/bin:/opt/alex/3.1.3/bin:/opt/happy/1.19.4/bin # necessary for building
ghc-pkg init /home/vagrant/.ghc/x86_64-linux-7.8.4/package.conf.d || true  # initialize the user ghc-db if missing
cabal sandbox init
cabal update
cabal sandbox add-source haxr-browser
cabal sandbox add-source opennebula
cabal install --only-dependencies
cabal install


echo
echo "Installing ABS Main Generator & Smart Deployer"
echo

# install needed tools
sudo apt-get update -y -q
sudo apt-get install -y -q cmake bison flex python-dev python-pip
sudo pip install antlr4-python2-runtime toposort psutil

# install gecode into /home/vagrant/gecode-4.4.0
cd /home/vagrant
wget http://www.gecode.org/download/gecode-4.4.0.tar.gz 
tar -zxvf gecode-4.4.0.tar.gz 
cd gecode-4.4.0
./configure --disable-examples --prefix=/home/vagrant/gecode-4.4.0
make && make install
rm -rf /home/vagrant/gecode-4.4.0.tar.gz
cat >>/home/vagrant/.bashrc <<EOF
export PATH=/home/vagrant/gecode-4.4.0/bin:\\\$PATH
EOF
# update LD_LIBRARY_PATH
if [ -z "$(grep 'export LD_LIBRARY_PATH=*' /home/vagrant/.bashrc)" ] ; then
cat >>/home/vagrant/.bashrc <<EOF
export LD_LIBRARY_PATH=\\\$LD_LIBRARY_PATH:/home/vagrant/gecode-4.4.0/lib
EOF
fi
chmod -R 755 /home/vagrant/gecode-4.4.0

# install minisearch in /home/vagrant/minisearch-0.1.0b1-Linux
cd /home/vagrant
wget http://www.minizinc.org/minisearch/minisearch-0.1.0b1-Linux64.tar.gz 
tar -zxvf minisearch-0.1.0b1-Linux64.tar.gz 
rm -rf minisearch-0.1.0b1-Linux64.tar.gz
cat >>/home/vagrant/.bashrc <<EOF
export PATH=/home/vagrant/minisearch-0.1.0b1-Linux/bin:\\\$PATH
EOF
chmod -R 755 /home/vagrant/minisearch-0.1.0b1-Linux


# install minizinc suite 1.6 in /home/vagrant/minizinc-1.6
cd /home/vagrant
wget http://www.minizinc.org/downloads/release-1.6/minizinc-1.6-x86_64-unknown-linux-gnu.tar.gz
tar -zxvf minizinc-1.6-x86_64-unknown-linux-gnu.tar.gz
rm -rf minizinc-1.6-x86_64-unknown-linux-gnu.tar.gz
cd /home/vagrant/minizinc-1.6
./SETUP
cat >>/home/vagrant/.bashrc <<EOF
export PATH=/home/vagrant/minizinc-1.6/bin:\\\$PATH
EOF
chmod -R 755 /home/vagrant/minizinc-1.6

# install smart_deployer in /home/vagrant/smart_deployer/abs_deployer
cd /home/vagrant
mkdir smart_deployer
cd smart_deployer
git clone --depth=1 -b smart_deployer https://github.com/jacopoMauro/abs_deployer.git
chmod -R 755 /home/vagrant/smart_deployer

# install main_generator in /home/vagrant/main_generator/abs_deployer
cd /home/vagrant
mkdir main_generator
cd main_generator
git clone --depth=1 https://github.com/jacopoMauro/abs_deployer.git
cat >>/home/vagrant/.bashrc <<EOF
export PATH=/home/vagrant/main_generator/abs_deployer/docker:\\\$PATH
EOF
chmod -R 755 /home/vagrant/main_generator 
chmod 775 /home/vagrant/main_generator/abs_deployer

# install zephyrus2 in /home/vagrant/zephyrus2
cd /home/vagrant/
git clone --depth=1 https://jacopomauro@bitbucket.org/jacopomauro/zephyrus2.git 
sudo pip install -e /home/vagrant/zephyrus2
sudo chown -R vagrant.vagrant /home/vagrant/zephyrus2
chmod -R 755 /home/vagrant/zephyrus2

# set corresponding paths in easyinterface
#
cp /var/www/easyinterface/server/bin/envisage/ENVISAGE_CONFIG /tmp
cat >> /tmp/ENVISAGE_CONFIG <<EOF
# path to SMART DEPLOYER
EC_SMARTDEPLOYERHOME="/home/vagrant/smart_deployer"
# path to MAIN GENERATOR
EC_MAINGENHOME="/home/vagrant/main_generator"
# 
EC_PATH="\$EC_PATH:/home/vagrant/main_generator/abs_deployer/docker:/home/vagrant/minizinc-1.6/bin:/home/vagrant/minisearch-0.1.0b1-Linux/bin:/home/vagrant/gecode-4.4.0/bin"
# 
EC_LD_LIBRARY_PATH="\$EC_LD_LIBRARY_PATH:/home/vagrant/gecode-4.4.0/lib"
EOF
sudo mv -f /tmp/ENVISAGE_CONFIG /var/www/easyinterface/server/bin/envisage



  SHELL
end


