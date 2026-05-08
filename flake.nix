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
          pythonPackages = pkgs.python312Packages;
        in
        {
          devShells.default = pkgs.mkShell {
            venvDir = "./.venv";
            nativeBuildInputs = with pkgs; [
              # languages
              jdk
              pythonPackages.python
              pythonPackages.venvShellHook

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
