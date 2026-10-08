// Fonte única da versão exibida nas telas (sidebar de todas as páginas logadas e rodapé
// do login): o "version" do package.json, que o scripts/release.sh mantém sincronizado
// com VERSION e pom.xml a cada release. Antes era um literal duplicado aqui, que o
// release.sh NÃO atualizava — a tela ficaria pra trás no primeiro bump de versão.
// O Vite resolve import de JSON em build (vira constante no bundle, não vai o arquivo).
import { version } from "../package.json";

export const APP_VERSION = version;
