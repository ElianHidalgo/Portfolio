import { Component } from '@angular/core';

/** Source code of this site, linked from the footer. */
const SOURCE_URL = 'https://github.com/ElianHidalgo/Portfolio';

@Component({
  selector: 'app-site-footer',
  templateUrl: './site-footer.html',
  styleUrl: './site-footer.scss',
})
export class SiteFooter {
  protected readonly sourceUrl = SOURCE_URL;
  protected readonly year = new Date().getFullYear();
}
