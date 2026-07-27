import { Link } from 'react-router-dom';
import { Container, Row, Col } from 'react-bootstrap';
import {
  FaFacebookF, FaInstagram, FaYoutube, FaPhone, FaMapMarkerAlt, FaEnvelope
} from 'react-icons/fa';

const HOTLINE_DISPLAY = '0914.077.779';
const HOTLINE_TEL = '0914077779';
const HEAD_OFFICE = '338 Trần Khát Chân, Thanh Nhàn, Hai Bà Trưng, Hà Nội';

export default function PublicFooter() {
  return (
    <footer style={{ backgroundColor: 'var(--ralsei-green)', color: 'var(--ralsei-black)' }} className="pt-5 pb-4 mt-auto">
      <Container>
        <Row className="gy-5">

          {/* Cột 1: Logo + Giới thiệu */}
          <Col lg={5} md={6}>
            <div className="d-flex align-items-center mb-3">
              <img
                src="/images/ralseiiii.jpg"
                alt="Ralsei Logo"
                height="65"
                className="rounded me-3 bg-white shadow-sm"
              />
              <div>
                <h5 className="fw-bold mb-0 text-uppercase">HOLA RALSEI</h5>
                <small className="opacity-75">Du lịch - Xe khách chất lượng cao</small>
              </div>
            </div>

            <p className="opacity-80 mb-4" style={{ fontSize: '0.95rem', lineHeight: '1.7' }}>
              Nhà xe Ralsei tự hào mang đến dịch vụ vận chuyển hành khách uy tín,
              an toàn và tiện nghi nhất trên các tuyến đường lớn.
            </p>

            <div className="d-flex gap-3">
              <a href="#" className="hover-opacity-100">
                <FaFacebookF size={20} />
              </a>
              <a href="#" className="hover-opacity-100">
                <FaInstagram size={20} />
              </a>
              <a href="#" className="hover-opacity-100">
                <FaYoutube size={20} />
              </a>
            </div>
          </Col>

          {/* Cột 2: Dịch vụ */}
          <Col lg={2} md={6} sm={6}>
            <h6 className="fw-bold mb-3 text-uppercase">Dịch vụ</h6>
            <ul className="list-unstyled mb-0" style={{ fontSize: '0.95rem', lineHeight: '2' }}>
              <li><Link to="/" className="text-decoration-none">Đặt vé</Link></li>
              <li><Link to="/cargo-history" className="text-decoration-none">Đơn hàng</Link></li>
            </ul>
          </Col>

          {/* Cột 3: Hỗ trợ */}
          <Col lg={2} md={6} sm={6}>
            <h6 className="fw-bold mb-3 text-uppercase">Hỗ trợ</h6>
            <ul className="list-unstyled mb-0" style={{ fontSize: '0.95rem', lineHeight: '2' }}>
              <li><a href="#" className="text-decoration-none">Điều khoản sử dụng</a></li>
              <li><a href="#" className="text-decoration-none">Chính sách hoàn vé</a></li>
              <li><a href="#" className="text-decoration-none">Câu hỏi thường gặp</a></li>
              <li><a href="#" className="text-decoration-none">Liên hệ</a></li>
            </ul>
          </Col>

          {/* Cột 4: Thông tin liên hệ */}
          <Col lg={3} md={6}>
            <h6 className="fw-bold mb-3 text-uppercase">Liên hệ</h6>

            <div className="d-flex align-items-start gap-3 mb-3">
              <FaMapMarkerAlt className="mt-1" />
              <div style={{ fontSize: '0.95rem' }}>
                <div className="fw-semibold">Trụ sở</div>
                {HEAD_OFFICE}
              </div>
            </div>

            <div className="d-flex align-items-center gap-3 mb-3">
              <FaPhone className="" />
              <div>
                <a href={`tel:${HOTLINE_TEL}`} className="text-decoration-none">
                  {HOTLINE_DISPLAY}
                </a>
              </div>
            </div>

            <div className="d-flex align-items-center gap-3">
              <FaEnvelope className="" />
              <a href="mailto:info@ralsei.vn" className="text-decoration-none">
                info@ralsei.vn
              </a>
            </div>
          </Col>
        </Row>

        <hr className="border-dark opacity-25 my-4" />

        {/* Bottom Bar */}
        <Row className="align-items-center">
          <Col md={6} className="text-center text-md-start mb-3 mb-md-0">
            <small className="opacity-75">
              © {new Date().getFullYear()} Công ty TNHH Du lịch Hola Ralsei. All rights reserved.
            </small>
          </Col>
          <Col md={6} className="text-center text-md-end">
            <small className="opacity-75">
              Chịu trách nhiệm nội dung: Ralsei Team
            </small>
          </Col>
        </Row>
      </Container>
    </footer>
  );
}
