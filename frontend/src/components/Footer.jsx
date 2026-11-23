import { ArrowUpRight } from "lucide-react";
import { Link } from "react-router-dom";

export default function Footer() {
  return (
    <footer id="story" className="mt-24 bg-[#26392d] text-[#f3f3ec]">
      <div className="mx-auto grid max-w-[1440px] gap-12 px-6 py-16 sm:px-10 md:grid-cols-[1.4fr_1fr_1fr] lg:px-16 lg:py-20">
        <div>
          <Link to="/" className="text-lg font-semibold tracking-[0.13em]">FORM <span className="font-normal text-[#9fa99e]">&</span> FIELD</Link>
          <p className="mt-5 max-w-sm text-sm leading-7 text-[#c5ccc1]">Fewer, better things for a life well lived. Thoughtful everyday essentials, selected with care.</p>
        </div>
        <div>
          <h2 className="text-xs font-semibold uppercase tracking-[0.16em] text-[#aeb9ac]">Explore</h2>
          <div className="mt-5 flex flex-col gap-3 text-sm text-[#e2e5dd]">
            <Link to="/" className="hover:text-white">Shop all</Link>
            <Link to="/cart" className="hover:text-white">Your bag</Link>
            <a href="mailto:hello@formandfield.example" className="inline-flex items-center gap-1 hover:text-white">Get in touch <ArrowUpRight size={13} /></a>
          </div>
        </div>
        <div>
          <h2 className="text-xs font-semibold uppercase tracking-[0.16em] text-[#aeb9ac]">A little note</h2>
          <p className="mt-5 text-sm leading-7 text-[#c5ccc1]">Made to be used, loved, and kept. Thank you for choosing more considered design.</p>
        </div>
      </div>
      <div className="border-t border-white/10">
        <div className="mx-auto flex max-w-[1440px] flex-col gap-2 px-6 py-5 text-xs text-[#aeb9ac] sm:flex-row sm:justify-between sm:px-10 lg:px-16">
          <span>© {new Date().getFullYear()} Form & Field. All rights reserved.</span><span>Made for everyday living.</span>
        </div>
      </div>
    </footer>
  );
}
