sudo apt-get install aspcud m4 unzip
wget https://raw.github.com/ocaml/opam/master/shell/opam_installer.sh -O - \
    | sh -s /usr/local/bin
eval `opam config env`
opam install core async