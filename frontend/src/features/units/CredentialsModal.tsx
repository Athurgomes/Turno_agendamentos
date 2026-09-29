/**
 * Modal de credenciais (RF-UNI-02, RN-03): mostra usuário e senha temporária
 * uma única vez, com "Copiar" e "Enviar por WhatsApp" para o telefone do
 * morador principal. Usado após cadastro, reset de senha e troca de
 * titularidade. `credentials` deve sumir do estado do componente pai ao
 * fechar (nunca fica em cache do TanStack Query) — este componente não
 * guarda a senha em lugar nenhum além da renderização enquanto `open`.
 */
import { useState } from "react";
import { Modal } from "../../shared/components/Modal";
import { buildWhatsAppMessage, buildWhatsAppUrl } from "./whatsappMessage";
import type { Credentials } from "../../shared/api/types";

export interface CredentialsModalProps {
  open: boolean;
  credentials: Credentials | null;
  /** Nome do morador principal, usado na mensagem de WhatsApp. */
  residentName: string;
  /** Telefone do morador principal, só dígitos com DDI (`docs/03` "Convenções"). */
  residentPhone: string;
  onClose: () => void;
}

export function CredentialsModal({
  open,
  credentials,
  residentName,
  residentPhone,
  onClose,
}: CredentialsModalProps) {
  if (!open || !credentials) return null;

  return (
    <Modal open={open} onClose={onClose} titleId="credentials-modal-title" title="Acesso gerado">
      {/* `key` remonta o corpo a cada novo par de credenciais, então "Copiado!" nunca sobra de uma senha anterior. */}
      <CredentialsModalBody
        key={`${credentials.username}:${credentials.tempPassword}`}
        credentials={credentials}
        residentName={residentName}
        residentPhone={residentPhone}
        onClose={onClose}
      />
    </Modal>
  );
}

function CredentialsModalBody({
  credentials,
  residentName,
  residentPhone,
  onClose,
}: {
  credentials: Credentials;
  residentName: string;
  residentPhone: string;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);
  const message = buildWhatsAppMessage(residentName, credentials, window.location.origin);
  const whatsappUrl = buildWhatsAppUrl(residentPhone, message);

  async function handleCopy() {
    await navigator.clipboard.writeText(
      `Usuário: ${credentials.username}\nSenha temporária: ${credentials.tempPassword}`,
    );
    setCopied(true);
  }

  return (
    <>
      <p className="rounded-sm border border-warning-800 bg-warning-50 px-3 py-2 text-sm text-warning-800">
        Anote ou envie agora: a senha temporária não será exibida novamente.
      </p>

      <dl className="mt-4 space-y-3 text-sm">
        <div>
          <dt className="font-medium text-neutral-700">Usuário</dt>
          <dd className="text-base text-neutral-900">{credentials.username}</dd>
        </div>
        <div>
          <dt className="font-medium text-neutral-700">Senha temporária</dt>
          <dd className="font-mono text-lg tracking-wide text-neutral-900">
            {credentials.tempPassword}
          </dd>
        </div>
      </dl>

      <div className="mt-6 flex flex-col gap-3 sm:flex-row" aria-live="polite">
        <button
          type="button"
          onClick={() => void handleCopy()}
          className="h-11 flex-1 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
        >
          {copied ? "Copiado!" : "Copiar"}
        </button>
        <a
          href={whatsappUrl}
          target="_blank"
          rel="noreferrer"
          className="flex h-11 flex-1 items-center justify-center rounded-md bg-primary-600 px-4 text-center text-sm font-medium text-white hover:bg-primary-700"
        >
          Enviar por WhatsApp
        </a>
      </div>

      <button
        type="button"
        onClick={onClose}
        className="mt-4 h-11 w-full rounded-md text-sm font-medium text-neutral-600 hover:bg-neutral-100"
      >
        Fechar
      </button>
    </>
  );
}
