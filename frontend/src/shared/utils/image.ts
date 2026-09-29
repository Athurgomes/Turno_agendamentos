/**
 * Validação e compressão de imagens no navegador antes do upload (RN-17,
 * `docs/07` §5): máx. 5 MB, JPG/PNG/WEBP, maior lado redimensionado para até
 * 1600 px via `<canvas>` nativo (sem biblioteca — CLAUDE.md §11 "zero
 * dependência nova"). A validação aqui é só UX; o backend confere o tipo real
 * pelos bytes iniciais.
 */
import { photoCountErrorMessage } from "./imageLimits";

const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_BYTES = 5 * 1024 * 1024;
const MAX_SIDE = 1600;

export function isAcceptedImageType(file: File): boolean {
  return ACCEPTED_TYPES.includes(file.type);
}

export function isWithinMaxSize(file: File): boolean {
  return file.size <= MAX_BYTES;
}

/** Mensagem pt-BR para o primeiro problema encontrado, ou `null` se o arquivo é válido. */
export function validateImageFile(file: File): string | null {
  if (!isAcceptedImageType(file)) {
    return "Formato não aceito. Envie uma foto em JPG, PNG ou WEBP.";
  }
  if (!isWithinMaxSize(file)) {
    return "Arquivo maior que 5 MB. Escolha uma foto menor.";
  }
  return null;
}

export type PhotoSelectionResult =
  | { files: File[]; error: null }
  | { files: null; error: string };

/**
 * Seleciona, valida e comprime fotos de um `<input type="file" multiple>`
 * (RN-17, RN-35, RF-REP-01/04): checa o limite de contagem (`currentCount` +
 * arquivos novos), o tipo/tamanho de cada arquivo e comprime os válidos.
 * Compartilhado entre o formulário de report e o upload de fotos do reparo —
 * a diferença entre as duas telas (soma à seleção existente ou substitui)
 * fica a cargo de quem chama.
 */
export async function selectAndCompressPhotos(
  fileList: FileList,
  currentCount: number,
  max: number,
): Promise<PhotoSelectionResult> {
  const files = Array.from(fileList);
  const countError = photoCountErrorMessage(currentCount + files.length, max);
  if (countError) return { files: null, error: countError };
  for (const file of files) {
    const invalidReason = validateImageFile(file);
    if (invalidReason) return { files: null, error: invalidReason };
  }
  const compressed = await Promise.all(files.map((file) => compressImage(file)));
  return { files: compressed, error: null };
}

/**
 * Redimensiona a imagem para até 1600px no maior lado e recomprime como JPEG.
 * `ponytail`: sem fila/worker — cada chamada abre e descarta seu próprio
 * `<img>`/`<canvas>`; ok para o volume de upload manual do MVP.
 */
export function compressImage(file: File): Promise<File> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const objectUrl = URL.createObjectURL(file);

    image.onload = () => {
      const scale = Math.min(1, MAX_SIDE / Math.max(image.width, image.height));
      const width = Math.round(image.width * scale);
      const height = Math.round(image.height * scale);

      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext("2d");
      if (!context) {
        URL.revokeObjectURL(objectUrl);
        resolve(file);
        return;
      }
      context.drawImage(image, 0, 0, width, height);

      canvas.toBlob(
        (blob) => {
          URL.revokeObjectURL(objectUrl);
          if (!blob) {
            resolve(file);
            return;
          }
          const compressed = new File(
            [blob],
            file.name.replace(/\.\w+$/, ".jpg"),
            { type: "image/jpeg" },
          );
          resolve(compressed);
        },
        "image/jpeg",
        0.85,
      );
    };

    image.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error("Não foi possível processar a imagem."));
    };

    image.src = objectUrl;
  });
}
