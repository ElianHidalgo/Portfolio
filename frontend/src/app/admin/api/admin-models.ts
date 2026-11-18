/** A contact message as the admin inbox API returns it. */
export interface AdminMessage {
  id: number;
  name: string;
  email: string;
  message: string;
  locale: 'es' | 'en';
  createdAt: string;
  read: boolean;
}

export interface MessagePage {
  items: AdminMessage[];
  total: number;
  unread: number;
}

/** Whether "Publicar" is configured, and when the site was last asked to rebuild. */
export interface PublishStatus {
  configured: boolean;
  lastRequestedAt: string | null;
}
