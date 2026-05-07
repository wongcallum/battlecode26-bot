{
  description = "Battlecode 2026";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
  };

  outputs =
    { flake-parts, ... }@inputs:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [ "x86_64-linux" ];
      perSystem =
        { pkgs, ... }:
        let
          jdk = pkgs.openjdk21_headless;
        in
        {
          devShells.default = pkgs.mkShell {
            packages = with pkgs; [
              # languages
              jdk
              python3
              uv

              # LSP
              (jdt-language-server.override {
                inherit jdk;
              })
              basedpyright

              # utils
              git
            ];
          };
        };
    };
}
