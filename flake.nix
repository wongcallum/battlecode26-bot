{
  description = "Battlecode 2026";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";

    # electron client built from source
    battlecodeClient.url = "github:wongcallum/battlecode26/nix";
  };

  outputs =
    { flake-parts, ... }@inputs:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [ "x86_64-linux" ];
      perSystem =
        { pkgs, inputs', ... }:
        let
          jdk = pkgs.openjdk21_headless;
          pythonPackages = pkgs.python312Packages;
          battlecode-client = inputs'.battlecodeClient.packages.battlecode-client;
        in
        {
          # use `nix run .#client`
          packages.client = battlecode-client;
          apps.client = {
            type = "app";
            program = "${battlecode-client}/bin/battlecode-client";
          };

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

            preShellHook = ''
              if [ -f "$venvDir/bin/activate" ] && ! grep -qF "$PWD" "$venvDir/bin/activate"; then
                echo "Recreating venv '$venvDir' (was built for a different path)."
                rm -rf "$venvDir"
              fi
            '';

            postShellHook = ''
              venvpy="$venvDir/bin/python"
              "$venvpy" -c "import battlecode26" 2>/dev/null \
                || "$venvpy" run.py update \
                || echo "battlecode26 not installed; run 'python run.py update' when online."
            '';
          };
        };
    };
}
