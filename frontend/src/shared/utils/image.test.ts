import { describe, expect, it } from "vitest";
import { selectAndCompressPhotos } from "./image";

function fileList(files: File[]): FileList {
  const list: Record<number, File> & Pick<FileList, "length" | "item"> = {
    ...files,
    length: files.length,
    item: (index: number) => files[index] ?? null,
  };
  return list as unknown as FileList;
}

describe("selectAndCompressPhotos", () => {
  it("recusa quando a contagem (atual + nova) passa do limite", async () => {
    const files = fileList([new File(["a"], "a.jpg", { type: "image/jpeg" })]);
    const result = await selectAndCompressPhotos(files, 5, 5);

    expect(result.error).toBe("Selecione no máximo 5 fotos por vez.");
    expect(result.files).toBeNull();
  });

  it("recusa arquivo de formato não aceito antes de comprimir", async () => {
    const files = fileList([new File(["a"], "a.gif", { type: "image/gif" })]);
    const result = await selectAndCompressPhotos(files, 0, 5);

    expect(result.error).toBe("Formato não aceito. Envie uma foto em JPG, PNG ou WEBP.");
    expect(result.files).toBeNull();
  });
});
