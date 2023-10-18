{
  description = "BioBricks";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-23.05";
    nixpkgs-unstable.url = "github:nixos/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, nixpkgs-unstable, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      with import nixpkgs { inherit system; };
      let pkgs-unstable = import nixpkgs-unstable { inherit system; };
      in {
        devShells.default = mkShell {
          buildInputs = [
            clojure
            git
            jdk
            nodePackages.npm
            rlwrap # Used by clj
            pkgs-unstable.tailwindcss
            zprint
          ];
        };
      });
}
